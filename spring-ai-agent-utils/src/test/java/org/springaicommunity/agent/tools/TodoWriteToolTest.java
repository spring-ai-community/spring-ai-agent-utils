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
package org.springaicommunity.agent.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springaicommunity.agent.tools.TodoWriteTool;
import org.springaicommunity.agent.tools.TodoWriteTool.Todos;
import org.springaicommunity.agent.tools.TodoWriteTool.Todos.Status;
import org.springaicommunity.agent.tools.TodoWriteTool.Todos.TodoItem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link TodoWriteTool}.
 *
 * @author Christian Tzolov
 */
@DisplayName("TodoWriteTool Tests")
class TodoWriteToolTest {

	private AtomicReference<Todos> capturedTodos;

	private TodoWriteTool tool;

	@BeforeEach
	void setUp() {
		this.capturedTodos = new AtomicReference<>();
		this.tool = TodoWriteTool.builder()
			.todoListConsumer(this.capturedTodos::set)
			.build();
	}

	@Nested
	@DisplayName("Constructor Tests")
	class ConstructorTests {

		@Test
		@DisplayName("Should create tool with default consumer")
		void shouldCreateWithDefaultConsumer() {
			TodoWriteTool defaultTool = TodoWriteTool.builder().build();
			assertThat(defaultTool).isNotNull();
		}

		@Test
		@DisplayName("Should create tool with custom consumer")
		void shouldCreateWithCustomConsumer() {
			AtomicReference<Todos> ref = new AtomicReference<>();
			TodoWriteTool customTool = TodoWriteTool.builder()
				.todoListConsumer(ref::set)
				.build();
			assertThat(customTool).isNotNull();
		}

	}

	@Nested
	@DisplayName("Valid Todo Tests")
	class ValidTodoTests {

		@Test
		@DisplayName("Should accept valid todos with one pending task")
		void shouldAcceptValidTodosWithOnePendingTask() {
			List<TodoItem> items = List.of(new TodoItem("Fix bug", Status.pending, "Fixing bug"));
			Todos todos = new Todos(items);

			String result = TodoWriteToolTest.this.tool.todoWrite(todos);

			assertThat(result).contains("Todos have been modified successfully");
			assertThat(TodoWriteToolTest.this.capturedTodos.get()).isEqualTo(todos);
		}

		@Test
		@DisplayName("Should accept valid todos with one in_progress task")
		void shouldAcceptValidTodosWithOneInProgressTask() {
			List<TodoItem> items = List.of(new TodoItem("Implement feature", Status.in_progress, "Implementing feature"));
			Todos todos = new Todos(items);

			String result = TodoWriteToolTest.this.tool.todoWrite(todos);

			assertThat(result).contains("Todos have been modified successfully");
			assertThat(TodoWriteToolTest.this.capturedTodos.get()).isEqualTo(todos);
		}

		@Test
		@DisplayName("Should accept valid todos with multiple tasks and one in_progress")
		void shouldAcceptValidTodosWithMultipleTasksAndOneInProgress() {
			List<TodoItem> items = List.of(new TodoItem("Task 1", Status.completed, "Completing task 1"),
					new TodoItem("Task 2", Status.in_progress, "Working on task 2"),
					new TodoItem("Task 3", Status.pending, "Preparing task 3"));
			Todos todos = new Todos(items);

			String result = TodoWriteToolTest.this.tool.todoWrite(todos);

			assertThat(result).contains("Todos have been modified successfully");
			assertThat(TodoWriteToolTest.this.capturedTodos.get()).isEqualTo(todos);
			assertThat(TodoWriteToolTest.this.capturedTodos.get().todos()).hasSize(3);
		}

		@Test
		@DisplayName("Should accept valid todos with all completed tasks")
		void shouldAcceptValidTodosWithAllCompletedTasks() {
			List<TodoItem> items = List.of(new TodoItem("Task 1", Status.completed, "Completing task 1"),
					new TodoItem("Task 2", Status.completed, "Completing task 2"));
			Todos todos = new Todos(items);

			String result = TodoWriteToolTest.this.tool.todoWrite(todos);

			assertThat(result).contains("Todos have been modified successfully");
			assertThat(TodoWriteToolTest.this.capturedTodos.get()).isEqualTo(todos);
		}

		@Test
		@DisplayName("Should accept empty todo list")
		void shouldAcceptEmptyTodoList() {
			Todos todos = new Todos(new ArrayList<>());

			String result = TodoWriteToolTest.this.tool.todoWrite(todos);

			assertThat(result).contains("Todos have been modified successfully");
			assertThat(TodoWriteToolTest.this.capturedTodos.get()).isEqualTo(todos);
		}

	}

	@Nested
	@DisplayName("Validation - Multiple In Progress Tests")
	class MultipleInProgressTests {

		@Test
		@DisplayName("Should reject todos with two in_progress tasks")
		void shouldRejectTodosWithTwoInProgressTasks() {
			List<TodoItem> items = List.of(new TodoItem("Task 1", Status.in_progress, "Working on task 1"),
					new TodoItem("Task 2", Status.in_progress, "Working on task 2"));
			Todos todos = new Todos(items);

			assertThatThrownBy(() -> TodoWriteToolTest.this.tool.todoWrite(todos))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Only ONE task can be in_progress at a time")
				.hasMessageContaining("Found 2 in_progress tasks");
		}

		@Test
		@DisplayName("Should reject todos with three in_progress tasks")
		void shouldRejectTodosWithThreeInProgressTasks() {
			List<TodoItem> items = List.of(new TodoItem("Task 1", Status.in_progress, "Working on task 1"),
					new TodoItem("Task 2", Status.in_progress, "Working on task 2"),
					new TodoItem("Task 3", Status.in_progress, "Working on task 3"));
			Todos todos = new Todos(items);

			assertThatThrownBy(() -> TodoWriteToolTest.this.tool.todoWrite(todos))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Only ONE task can be in_progress at a time")
				.hasMessageContaining("Found 3 in_progress tasks");
		}

	}

	@Nested
	@DisplayName("Validation - Empty Content Tests")
	class EmptyContentTests {

		@Test
		@DisplayName("Should reject todo with null content")
		void shouldRejectTodoWithNullContent() {
			List<TodoItem> items = List.of(new TodoItem(null, Status.pending, "Doing something"));
			Todos todos = new Todos(items);

			assertThatThrownBy(() -> TodoWriteToolTest.this.tool.todoWrite(todos))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("has empty or blank content");
		}

		@Test
		@DisplayName("Should reject todo with empty content")
		void shouldRejectTodoWithEmptyContent() {
			List<TodoItem> items = List.of(new TodoItem("", Status.pending, "Doing something"));
			Todos todos = new Todos(items);

			assertThatThrownBy(() -> TodoWriteToolTest.this.tool.todoWrite(todos))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("has empty or blank content");
		}

		@Test
		@DisplayName("Should reject todo with blank content")
		void shouldRejectTodoWithBlankContent() {
			List<TodoItem> items = List.of(new TodoItem("   ", Status.pending, "Doing something"));
			Todos todos = new Todos(items);

			assertThatThrownBy(() -> TodoWriteToolTest.this.tool.todoWrite(todos))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("has empty or blank content");
		}

		@Test
		@DisplayName("Should reject second todo with blank content")
		void shouldRejectSecondTodoWithBlankContent() {
			List<TodoItem> items = List.of(new TodoItem("Valid task", Status.pending, "Doing valid task"),
					new TodoItem("   ", Status.pending, "Doing something"));
			Todos todos = new Todos(items);

			assertThatThrownBy(() -> TodoWriteToolTest.this.tool.todoWrite(todos))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Task at index 1")
				.hasMessageContaining("has empty or blank content");
		}

	}

	@Nested
	@DisplayName("Validation - Empty ActiveForm Tests")
	class EmptyActiveFormTests {

		@Test
		@DisplayName("Should reject todo with null activeForm")
		void shouldRejectTodoWithNullActiveForm() {
			List<TodoItem> items = List.of(new TodoItem("Fix bug", Status.pending, null));
			Todos todos = new Todos(items);

			assertThatThrownBy(() -> TodoWriteToolTest.this.tool.todoWrite(todos))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("has empty or blank activeForm");
		}

		@Test
		@DisplayName("Should reject todo with empty activeForm")
		void shouldRejectTodoWithEmptyActiveForm() {
			List<TodoItem> items = List.of(new TodoItem("Fix bug", Status.pending, ""));
			Todos todos = new Todos(items);

			assertThatThrownBy(() -> TodoWriteToolTest.this.tool.todoWrite(todos))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("has empty or blank activeForm");
		}

		@Test
		@DisplayName("Should reject todo with blank activeForm")
		void shouldRejectTodoWithBlankActiveForm() {
			List<TodoItem> items = List.of(new TodoItem("Fix bug", Status.pending, "   "));
			Todos todos = new Todos(items);

			assertThatThrownBy(() -> TodoWriteToolTest.this.tool.todoWrite(todos))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("has empty or blank activeForm");
		}

	}

	@Nested
	@DisplayName("Validation - Null Status Tests")
	class NullStatusTests {

		@Test
		@DisplayName("Should reject todo with null status")
		void shouldRejectTodoWithNullStatus() {
			List<TodoItem> items = List.of(new TodoItem("Fix bug", null, "Fixing bug"));
			Todos todos = new Todos(items);

			assertThatThrownBy(() -> TodoWriteToolTest.this.tool.todoWrite(todos))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("has null status")
				.hasMessageContaining("Status must be one of: pending, in_progress, completed");
		}

	}

	@Nested
	@DisplayName("Validation - Null Todos Tests")
	class NullTodosTests {

		@Test
		@DisplayName("Should reject null todos")
		void shouldRejectNullTodos() {
			assertThatThrownBy(() -> TodoWriteToolTest.this.tool.todoWrite(null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Todos cannot be null");
		}

		@Test
		@DisplayName("Should reject null todo list")
		void shouldRejectNullTodoList() {
			Todos todos = new Todos(null);

			assertThatThrownBy(() -> TodoWriteToolTest.this.tool.todoWrite(todos))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Todos cannot be null");
		}

		@Test
		@DisplayName("Should reject null todo item")
		void shouldRejectNullTodoItem() {
			List<TodoItem> items = new ArrayList<>();
			items.add(null);
			Todos todos = new Todos(items);

			assertThatThrownBy(() -> TodoWriteToolTest.this.tool.todoWrite(todos))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Task at index 0 is null");
		}

	}

	@Nested
	@DisplayName("Record Tests")
	class RecordTests {

		@Test
		@DisplayName("TodoItem should be a record with proper fields")
		void todoItemShouldBeRecordWithProperFields() {
			TodoItem item = new TodoItem("Test task", Status.pending, "Testing task");

			assertThat(item.content()).isEqualTo("Test task");
			assertThat(item.status()).isEqualTo(Status.pending);
			assertThat(item.activeForm()).isEqualTo("Testing task");
		}

		@Test
		@DisplayName("Todos should be a record with proper fields")
		void todosShouldBeRecordWithProperFields() {
			List<TodoItem> items = List.of(new TodoItem("Task 1", Status.pending, "Doing task 1"));
			Todos todos = new Todos(items);

			assertThat(todos.todos()).isEqualTo(items);
			assertThat(todos.todos()).hasSize(1);
		}

		@Test
		@DisplayName("Status enum should have all expected values")
		void statusEnumShouldHaveAllExpectedValues() {
			assertThat(Status.values()).containsExactlyInAnyOrder(Status.pending, Status.in_progress,
					Status.completed);
		}

	}

	@Nested
	@DisplayName("Integration Tests")
	class IntegrationTests {

		@Test
		@DisplayName("Should handle workflow from pending to in_progress to completed")
		void shouldHandleWorkflowFromPendingToInProgressToCompleted() {
			// Initial state - all pending
			List<TodoItem> items1 = List.of(new TodoItem("Task 1", Status.pending, "Doing task 1"),
					new TodoItem("Task 2", Status.pending, "Doing task 2"));
			Todos todos1 = new Todos(items1);
			TodoWriteToolTest.this.tool.todoWrite(todos1);

			// Start working on task 1
			List<TodoItem> items2 = List.of(new TodoItem("Task 1", Status.in_progress, "Doing task 1"),
					new TodoItem("Task 2", Status.pending, "Doing task 2"));
			Todos todos2 = new Todos(items2);
			TodoWriteToolTest.this.tool.todoWrite(todos2);

			// Complete task 1, start task 2
			List<TodoItem> items3 = List.of(new TodoItem("Task 1", Status.completed, "Doing task 1"),
					new TodoItem("Task 2", Status.in_progress, "Doing task 2"));
			Todos todos3 = new Todos(items3);
			TodoWriteToolTest.this.tool.todoWrite(todos3);

			// Complete all tasks
			List<TodoItem> items4 = List.of(new TodoItem("Task 1", Status.completed, "Doing task 1"),
					new TodoItem("Task 2", Status.completed, "Doing task 2"));
			Todos todos4 = new Todos(items4);
			String result = TodoWriteToolTest.this.tool.todoWrite(todos4);

			assertThat(result).contains("Todos have been modified successfully");
		}

	}

}
