/*
 * Copyright 2025 - 2025 the original author or authors.
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
package org.springaicommunity.agent.utils;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springaicommunity.agent.tools.SkillsTool;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownParserBlockScalarTest {

	@ParameterizedTest
	@MethodSource("blockScalars")
	void parsesBlockScalarsWithoutTreatingContinuationLinesAsKeys(String indicator, String expected) {
		var parser = new MarkdownParser("---\nname: sample\ndescription: " + indicator
				+ "\n  Mandatory entry point: read first\n  before using the tool.\n\nlicense: MIT\n---\n# Body");

		assertThat(parser.getFrontMatter()).hasSize(3)
			.containsEntry("name", "sample")
			.containsEntry("description", expected)
			.containsEntry("license", "MIT");
		assertThat(parser.getContent()).isEqualTo("# Body");
	}

	static Stream<Arguments> blockScalars() {
		return Stream.of(Arguments.of(">", "Mandatory entry point: read first before using the tool.\n"),
				Arguments.of(">-", "Mandatory entry point: read first before using the tool."),
				Arguments.of(">+", "Mandatory entry point: read first before using the tool.\n\n"),
				Arguments.of("|", "Mandatory entry point: read first\nbefore using the tool.\n"),
				Arguments.of("|-", "Mandatory entry point: read first\nbefore using the tool."),
				Arguments.of("|+", "Mandatory entry point: read first\nbefore using the tool.\n\n"));
	}

	@Test
	void foldedScalarPreservesParagraphBreaksAtEndOfFrontMatter() {
		var parser = new MarkdownParser("---\ndescription: >-\n  First paragraph.\n\n  Second paragraph.\n---");

		assertThat(parser.getFrontMatter()).containsEntry("description", "First paragraph.\nSecond paragraph.");
	}

	@Test
	void literalScalarPreservesAdditionalIndentationAndNormalizesCrLf() {
		var parser = new MarkdownParser(
				"---\r\ndescription: |-\r\n  First line.\r\n    nested: detail\r\n  Last line.\r\n---");

		assertThat(parser.getFrontMatter()).hasSize(1)
			.containsEntry("description", "First line.\n  nested: detail\nLast line.");
	}

	@Test
	void keepScalarPreservesTrailingBlankLinesAtEndOfFrontMatter() {
		var parser = new MarkdownParser("---\ndescription: |+\n  First line.\n\n\n---");

		assertThat(parser.getFrontMatter()).containsEntry("description", "First line.\n\n\n");
	}

	@Test
	void emptyScalarDoesNotConsumeFollowingField() {
		var parser = new MarkdownParser("---\ndescription: >\nlicense: MIT\n---");

		assertThat(parser.getFrontMatter()).hasSize(2).containsEntry("description", "").containsEntry("license", "MIT");
	}

	@Test
	void blockIndentationIsRelativeToItsKey() {
		var parser = new MarkdownParser(
				"---\n  description: >-\n    Read first: use this tool.\n    Then continue.\n  license: MIT\n---");

		assertThat(parser.getFrontMatter()).hasSize(2)
			.containsEntry("description", "Read first: use this tool. Then continue.")
			.containsEntry("license", "MIT");
	}

	@Test
	void quotedIndicatorsRemainOrdinaryStrings() {
		var parser = new MarkdownParser("---\nfolded: \">\"\nliteral: '|-'\n---");

		assertThat(parser.getFrontMatter()).containsEntry("folded", ">").containsEntry("literal", "|-");
	}

	@Test
	void skillAdvertisementContainsTheCompleteDescriptionWithoutGhostElements() {
		var parser = new MarkdownParser(
				"---\nname: sample\ndescription: >-\n  Mandatory entry point: read first\n  before using the tool.\n---\n# Body");
		var skill = new SkillsTool.Skill("/skills/sample", parser.getFrontMatter(), parser.getContent());

		assertThat(skill.toXml())
			.contains("<description>Mandatory entry point: read first before using the tool.</description>")
			.doesNotContain("<Mandatory entry point>");
	}

}
