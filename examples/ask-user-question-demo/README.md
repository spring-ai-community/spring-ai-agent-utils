# AskUserQuestionTool Demo

A Spring Boot console application demonstrating the [AskUserQuestionTool](../../spring-ai-agent-utils/docs/AskUserQuestionTool.md) - a tool that enables AI agents to ask users clarifying questions during execution.

## Overview

This demo shows how AI agents can interactively gather user preferences, clarify ambiguous instructions, and make implementation decisions by asking questions with multiple-choice options.

The application runs as a console-based chat interface where you can interact with an AI agent. When the agent needs additional information to fulfill your request, it will automatically use the `AskUserQuestionTool` to ask you questions with predefined options or allow free-text input.

## Features

- Console-based interactive chat with AI agent
- Automatic question prompting when the agent needs clarification
- Support for single-select and multi-select questions
- Free-text input support beyond predefined options
- Conversation memory to maintain context across interactions

## Prerequisites

- Java 17 or higher
- Maven 3.6 or higher
- Anthropic API key (or configure alternative AI provider)

## Configuration

Set your API key as an environment variable:

```bash
export ANTHROPIC_API_KEY=your-api-key-here
```

### Alternative AI Providers

The demo is pre-configured for Anthropic's Claude, but you can switch to other providers by uncommenting the relevant sections in [application.properties](src/main/resources/application.properties):

- **OpenAI**: Uncomment the OpenAI SDK configuration and dependency
- **Google GenAI**: Uncomment the Google GenAI configuration and dependency

## Running the Demo

From the project root directory:

```bash
cd examples/ask-user-question-demo
mvn spring-boot:run
```

Or build and run the JAR:

```bash
mvn clean package
java -jar target/chat-demo2-0.1.0-SNAPSHOT.jar
```

## Usage

Once started, the application will greet you and wait for input:

```
I am your assistant.

USER:
```

### Example Interactions

**Example 1: Choosing a technology stack**

```
USER: Help me choose a database for my new web application

ASSISTANT: I'd be happy to help you choose a database. Let me ask you a few questions to understand your needs better.

Database Type: What type of database do you prefer?
  1. PostgreSQL - Powerful open-source relational database with ACID compliance
  2. MongoDB - Flexible document-oriented NoSQL database
  3. Redis - In-memory key-value store for caching and real-time data
  (Enter a number, or type custom text)
1

Scale: What scale are you expecting?
  1. Small - < 1000 users, simple queries
  2. Medium - 1000-100K users, moderate complexity
  3. Large - > 100K users, high performance needs
  (Enter a number, or type custom text)
2

ASSISTANT: Based on your requirements, PostgreSQL is an excellent choice...
```

**Example 2: Multi-select question**

```
USER: I'm building a REST API. What features should I include?

ASSISTANT: Let me help you decide on the features.

Features: Which features do you want to enable?
  1. Authentication - User login and registration with JWT
  2. Database - PostgreSQL integration with JPA
  3. Caching - Redis caching layer for performance
  4. Monitoring - Health checks and metrics endpoints
  (Enter numbers separated by commas, or type custom text)
1,2,4

ASSISTANT: Great choices! I'll help you set up authentication, database integration, and monitoring...
```

## How It Works

The demo implements the `AskUserQuestionTool` with a console-based question handler:

1. The AI agent determines it needs user input and calls the `AskUserQuestionTool`
2. The tool invokes the custom `handleQuestions` function with the questions
3. Questions are displayed in the console with numbered options
4. User enters their selection (numbers or free text)
5. Answers are parsed and returned to the AI agent
6. The agent continues with the collected information

Key implementation details in [Application.java](src/main/java/org/springaicommunity/agent/Application.java):

- **Lines 55-58**: Tool configuration with custom question handler
- **Lines 81-106**: Console-based question display and answer collection
- **Lines 108-125**: Response parsing (converts numbers to option labels or uses free text)

## Customization

### Disable Answer Validation

By default, the tool validates that all questions are answered. To allow partial answers, set `answersValidation(false)` when building the tool (see line 57 in [Application.java](src/main/java/org/springaicommunity/agent/Application.java#L57)).

### Adjust Chat Memory

The demo keeps the last 500 messages in memory (line 64). Adjust this value based on your needs:

```java
MessageChatMemoryAdvisor.builder(
    MessageWindowChatMemory.builder()
        .maxMessages(100)  // Change this value
        .build()
).build()
```
### Change AI Provider

Edit the model and provider configuration in [application.properties](src/main/resources/application.properties#L7) as well as the pom dependecies.

## Learn More

- [AskUserQuestionTool Documentation](../../spring-ai-agent-utils/docs/AskUserQuestionTool.md)
- [Spring AI Documentation](https://docs.spring.io/spring-ai/reference/)
- [Claude Agent SDK - User Input](https://platform.claude.com/docs/en/agent-sdk/user-input#question-format)

## Project Structure

```
ask-user-question-demo/
├── pom.xml                           # Maven configuration
├── src/
│   └── main/
│       ├── java/
│       │   └── org/springaicommunity/agent/
│       │       └── Application.java  # Main application and question handler
│       └── resources/
│           └── application.properties # Configuration
└── README.md
```
