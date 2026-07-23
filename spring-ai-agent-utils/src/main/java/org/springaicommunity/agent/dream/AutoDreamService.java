/*
* Copyright 2026 - 2026 the original author or authors.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* https://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package org.springaicommunity.agent.dream;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.agent.tools.AutoMemoryTools;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.session.SessionService;
import org.springframework.ai.session.tool.CrossSessionRecallTools;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Runs an out-of-band memory-consolidation ("dream") cycle: a dedicated subagent — the
 * Dreamer — reviews an {@code AutoMemoryTools} memory store and deduplicates, prunes, and
 * reorganizes it, independently of any live conversation.
 *
 * <p>
 * Each call to {@link #runDreamCycle(String)} builds its own short-lived {@link ChatClient}
 * scoped to exactly the tools the Dreamer is allowed to use — never the caller's tools —
 * guarded by a {@link DreamLock} so two cycles never run concurrently against the same
 * memory store.
 *
 * <p>
 * When {@link Builder#sessionService(SessionService)} is configured, {@link
 * #runDreamCycle(String, String)} additionally gives the Dreamer a read-only
 * {@code cross_session_search} tool ({@link CrossSessionRecallTools}) scoped to the given
 * {@code userId}, so it can mine historical conversation signal in addition to
 * self-consolidating the memory store. Without a configured {@code sessionService} (or
 * without a {@code userId}), the Dreamer only ever sees {@code AutoMemoryTools} — this is
 * an additive, opt-in capability, not a requirement.
 *
 * @author Christian Tzolov
 */
public class AutoDreamService {

	private static final Logger logger = LoggerFactory.getLogger(AutoDreamService.class);

	private static final Resource DEFAULT_DREAM_SYSTEM_PROMPT = new DefaultResourceLoader()
		.getResource("classpath:/prompt/AUTO_DREAM_SYSTEM_PROMPT.md");

	static final String PROPOSALS_DIR_NAME = ".dream-proposals";

	private static final DateTimeFormatter PROPOSAL_STAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssSSS")
		.withZone(ZoneOffset.UTC);

	private final ChatClient.Builder chatClientBuilder;

	private final String dreamSystemPrompt;

	private final DreamMode dreamMode;

	private final Duration staleLockAfter;

	private final SessionService sessionService;

	private AutoDreamService(ChatClient.Builder chatClientBuilder, String dreamSystemPrompt, DreamMode dreamMode,
			Duration staleLockAfter, SessionService sessionService) {
		this.chatClientBuilder = chatClientBuilder;
		this.dreamSystemPrompt = dreamSystemPrompt;
		this.dreamMode = dreamMode;
		this.staleLockAfter = staleLockAfter;
		this.sessionService = sessionService;
	}

	/**
	 * Runs one memory-only dream cycle against {@code memoriesRootDirectory} — equivalent
	 * to {@code runDreamCycle(memoriesRootDirectory, null)}. Safe to call synchronously or
	 * from a background task — this method blocks until the Dreamer finishes.
	 * @param memoriesRootDirectory the memories root directory to consolidate
	 * @return the outcome of the cycle; {@link DreamResult#skipped(String)} if another
	 * cycle already holds the lock for this directory
	 */
	public DreamResult runDreamCycle(String memoriesRootDirectory) {
		return this.runDreamCycle(memoriesRootDirectory, null);
	}

	/**
	 * Runs one dream cycle against {@code memoriesRootDirectory}. When both a
	 * {@code sessionService} was configured on the builder and {@code userId} is
	 * non-empty, the Dreamer additionally gets a read-only {@code cross_session_search}
	 * tool scoped to that user's session history; otherwise this behaves exactly like
	 * {@link #runDreamCycle(String)}.
	 * @param memoriesRootDirectory the memories root directory to consolidate
	 * @param userId whose session history to make searchable, or {@code null}/empty for
	 * memory-only dreaming
	 * @return the outcome of the cycle; {@link DreamResult#skipped(String)} if another
	 * cycle already holds the lock for this directory
	 */
	public DreamResult runDreamCycle(String memoriesRootDirectory, String userId) {
		Assert.hasText(memoriesRootDirectory, "memoriesRootDirectory must not be empty");

		Path liveMemoriesDir = Paths.get(memoriesRootDirectory);
		try {
			Files.createDirectories(liveMemoriesDir);
		}
		catch (IOException e) {
			throw new UncheckedIOException("Failed to create memories directory: " + liveMemoriesDir, e);
		}

		Optional<DreamLock> lock = DreamLock.tryAcquire(liveMemoriesDir, this.staleLockAfter);
		if (lock.isEmpty()) {
			return DreamResult.skipped("A dream cycle is already running for " + memoriesRootDirectory);
		}

		try (DreamLock acquired = lock.get()) {
			return this.doDream(liveMemoriesDir, userId);
		}
	}

	private DreamResult doDream(Path liveMemoriesDir, String userId) {

		DreamState state = DreamState.load(liveMemoriesDir);
		Path dreamTargetDir = this.prepareTargetMemoriesDir(liveMemoriesDir);

		boolean crossSessionRecallAvailable = this.sessionService != null && StringUtils.hasText(userId);

		List<ToolCallback> toolCallbacks = new ArrayList<>(Arrays.asList(MethodToolCallbackProvider.builder()
			.toolObjects(AutoMemoryTools.builder().memoriesDir(dreamTargetDir).build())
			.build()
			.getToolCallbacks()));

		if (crossSessionRecallAvailable) {
			toolCallbacks.addAll(Arrays.asList(MethodToolCallbackProvider.builder()
				.toolObjects(CrossSessionRecallTools.builder(this.sessionService, userId).build())
				.build()
				.getToolCallbacks()));
		}

		ChatClient dreamerChatClient = this.chatClientBuilder.clone()
			.defaultTools(toolCallbacks)
			// .defaultAdvisors(ToolCallingAdvisor.builder().build()) // toolcalling advisor is auto-enabled by default.
			.build();

		String status;
		String summary;
		try {
			summary = dreamerChatClient.prompt()
				.system(this.dreamSystemPrompt)
				.user(this.buildKickoffPrompt(state, crossSessionRecallAvailable))
				.call()
				.content();
			status = "completed";
		}
		catch (Exception ex) {
			logger.error("Dream cycle failed for {}", liveMemoriesDir, ex);
			summary = "Dream cycle failed: " + ex.getMessage();
			status = "failed";
		}

		DreamState updated = state.withDreamCompleted(Instant.now(), status, summary);
		updated.save(liveMemoriesDir);

		return new DreamResult(status, summary);
	}

	/**
	 * In {@link DreamMode#AUTO_APPLY} the Dreamer edits the live memory store directly.
	 * In {@link DreamMode#PROPOSE} it edits a timestamped copy instead, leaving the live
	 * store untouched.
	 */
	private Path prepareTargetMemoriesDir(Path liveMemoriesDir) {
		if (this.dreamMode == DreamMode.AUTO_APPLY) {
			return liveMemoriesDir;
		}
		String stamp = PROPOSAL_STAMP_FORMAT.format(Instant.now());
		Path proposalDir = liveMemoriesDir.resolve(PROPOSALS_DIR_NAME).resolve(stamp);
		this.copyMemoriesTree(liveMemoriesDir, proposalDir);
		return proposalDir;
	}

	private void copyMemoriesTree(Path source, Path target) {
		try {
			Files.createDirectories(target);
			try (Stream<Path> walk = Files.walk(source)) {
				for (Path path : walk.filter(p -> !this.isDreamInternal(source, p)).toList()) {
					Path relative = source.relativize(path);
					if (relative.toString().isEmpty()) {
						continue;
					}
					Path destination = target.resolve(relative.toString());
					if (Files.isDirectory(path)) {
						Files.createDirectories(destination);
					}
					else {
						Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
					}
				}
			}
		}
		catch (IOException e) {
			throw new UncheckedIOException("Failed to prepare dream proposal directory: " + target, e);
		}
	}

	/**
	 * Excludes the dream bookkeeping files themselves (and the proposals directory) from
	 * the copy made for {@link DreamMode#PROPOSE}.
	 */
	private boolean isDreamInternal(Path root, Path path) {
		Path relative = root.relativize(path);
		if (relative.toString().isEmpty()) {
			return false;
		}
		String top = relative.getName(0).toString();
		return top.equals(DreamState.STATE_FILE_NAME) || top.equals(DreamLock.LOCK_FILE_NAME)
				|| top.equals(PROPOSALS_DIR_NAME);
	}

	private String buildKickoffPrompt(DreamState state, boolean crossSessionRecallAvailable) {
		String lastDream = state.lastDreamAt() != null ? state.lastDreamAt() : "never (this is the first dream cycle)";
		String base = """
				Begin a memory-dream consolidation cycle now.
				Last dream completed at: %s
				Conversation turns processed since then: %d

				Follow the four-phase process from your operating instructions and finish by \
				returning a short summary of what you changed (or "no changes needed" if the \
				memory store was already clean).
				""".formatted(lastDream, state.sessionsSinceLastDream());

		if (!crossSessionRecallAvailable) {
			return base;
		}

		String sinceGuidance = state.lastDreamAt() != null
				? "scope your queries with since = " + state.lastDreamAt()
						+ " so you only see history from after the last dream"
				: "this is the first dream cycle, so omit 'since' and search the full history";

		return base + """

				The cross_session_search tool is available this cycle. Use it during the \
				"gather signal" phase to look for corrections, explicit "remember this" \
				requests, and decisions across past sessions — %s.
				""".formatted(sinceGuidance);
	}

	public static Builder builder(ChatClient.Builder chatClientBuilder) {
		return new Builder(chatClientBuilder);
	}

	public static final class Builder {

		private final ChatClient.Builder chatClientBuilder;

		private Resource dreamSystemPrompt = DEFAULT_DREAM_SYSTEM_PROMPT;

		private DreamMode dreamMode = DreamMode.AUTO_APPLY;

		private Duration staleLockAfter = Duration.ofMinutes(15);

		private SessionService sessionService;

		private Builder(ChatClient.Builder chatClientBuilder) {
			Assert.notNull(chatClientBuilder, "chatClientBuilder must not be null");
			this.chatClientBuilder = chatClientBuilder;
		}

		public Builder dreamSystemPrompt(Resource dreamSystemPrompt) {
			Assert.notNull(dreamSystemPrompt, "dreamSystemPrompt must not be null");
			this.dreamSystemPrompt = dreamSystemPrompt;
			return this;
		}

		public Builder dreamMode(DreamMode dreamMode) {
			Assert.notNull(dreamMode, "dreamMode must not be null");
			this.dreamMode = dreamMode;
			return this;
		}

		/**
		 * Age at which an unreleased {@code .dream.lock} is treated as abandoned (e.g.
		 * left behind by a crashed dream cycle) and reclaimed by the next attempt.
		 * Defaults to 15 minutes.
		 */
		public Builder staleLockAfter(Duration staleLockAfter) {
			Assert.notNull(staleLockAfter, "staleLockAfter must not be null");
			this.staleLockAfter = staleLockAfter;
			return this;
		}

		/**
		 * Optional. When set, {@link AutoDreamService#runDreamCycle(String, String)}
		 * gives the Dreamer a read-only {@code cross_session_search} tool scoped to
		 * whichever {@code userId} is passed to that call, in addition to
		 * {@code AutoMemoryTools}. Leave unset for memory-only dreaming.
		 */
		public Builder sessionService(SessionService sessionService) {
			Assert.notNull(sessionService, "sessionService must not be null");
			this.sessionService = sessionService;
			return this;
		}

		public AutoDreamService build() {
			String promptText;
			try {
				promptText = this.dreamSystemPrompt.getContentAsString(StandardCharsets.UTF_8);
			}
			catch (IOException e) {
				throw new IllegalStateException("Failed to read dream system prompt", e);
			}
			return new AutoDreamService(this.chatClientBuilder, promptText, this.dreamMode, this.staleLockAfter,
					this.sessionService);
		}

	}

}
