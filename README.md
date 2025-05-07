# Appian-Kafka Connector

## Overview

This project provides an Appian plugin that enables interaction with Apache Kafka. It allows Appian processes to:
- Produce messages to Kafka topics.
- Consume messages from Kafka topics using a polling mechanism.

The plugin implements two main smart services:
- `KafkaProducerSmartService`: Sends messages to a specified Kafka topic.
- `KafkaConsumerSmartService`: Polls a specified Kafka topic for messages and returns them.

## Prerequisites

- **Java Development Kit (JDK):** Version 17 or higher.
- **Apache Maven:** For building the project.
- **Appian Environment:** A running Appian instance to deploy and test the plugin (compatible with version specified in `plugin.xml`, e.g., 23.4+).
- **Apache Kafka Cluster:** A running Kafka cluster accessible from the Appian environment.
- **Appian SDK:** You need to have the Appian SDK JAR file. This is not typically available in public Maven repositories and must be obtained from Appian (e.g., from an Appian installation directory or a developer portal) and installed into your local Maven repository.

### Installing Appian SDK Locally

If the Appian SDK (e.g., `appian-sdk-24.1.jar`) is not in a shared Maven repository, you must install it into your local Maven repository. 
Navigate to the directory containing the Appian SDK JAR file and run a command similar to the following (adjust the file name and version accordingly):

```bash
mvn install:install-file -Dfile=appian-sdk-24.1.jar -DgroupId=com.appian -DartifactId=appian-sdk -Dversion=24.1 -Dpackaging=jar
```

Replace `appian-sdk-24.1.jar` and `24.1` with the actual file name and version of your SDK.

## Building the Plugin

1.  **Clone the repository (if you haven't already):**
    ```bash
    git clone <repository-url>
    cd <repository-directory>
    ```
2.  **Ensure Appian SDK is installed:** Follow the steps in the "Installing Appian SDK Locally" section if necessary.
3.  **Build the project using Maven:**
    ```bash
    mvn clean package
    ```
    This command will compile the code, run tests (if any), and package the plugin into a JAR file located in the `target/` directory (e.g., `Connector-0.0.1-SNAPSHOT.jar`).

## Plugin Configuration

The primary configuration for the Kafka connection is managed through the `plugin.xml` file (located in `src/main/resources/plugin.xml`). When the plugin is deployed to Appian, an administrator can configure these settings via the Admin Console.

Key configurable properties:
-   `kafka.bootstrap.servers`: (Required) Comma-separated list of Kafka broker addresses (e.g., `kafka-broker1:9092,kafka-broker2:9092`).
-   `kafka.security.protocol`: (Optional) Security protocol to use (e.g., `PLAINTEXT`, `SSL`, `SASL_SSL`). Defaults to `PLAINTEXT`.

Refer to the `plugin.xml` file for more details on available configuration properties. The `KafkaConfig.java` class reads these properties.

## Deployment to Appian

1.  Navigate to the **Admin Console** in your Appian environment.
2.  Go to the **Plug-ins** section.
3.  Click on **Deploy Plug-in**.
4.  Upload the JAR file generated in the `target/` directory (e.g., `Connector-0.0.1-SNAPSHOT.jar`).
5.  The plugin should appear in the list of installed plug-ins.

## Smart Service Usage

Once deployed, the Kafka smart services will be available in the Appian Process Modeler palette under "Integration Services" > "Kafka Messaging" (or as configured in `plugin.xml` and annotations).

### 1. KafkaProducerSmartService

-   **Display Name:** Produce Kafka Message
-   **Description:** Sends a message to a Kafka topic.
-   **Inputs:**
    -   `topic` (Text, Required): The Kafka topic to send the message to.
    -   `key` (Text, Optional): The key for the Kafka message.
    -   `value` (Text, Required): The value/content of the Kafka message.
-   **Output:**
    -   `success` (Boolean): `true` if the message was sent successfully, `false` otherwise.

### 2. KafkaConsumerSmartService

-   **Display Name:** Consume Kafka Messages
-   **Description:** Consumes messages from a Kafka topic using a polling mechanism.
-   **Inputs:**
    -   `topic` (Text, Required): The Kafka topic to subscribe to.
    -   `groupId` (Text, Required): The consumer group ID for the Kafka consumer.
    -   `pollTimeout` (Number - Integer, Required): Duration in milliseconds to poll for messages.
-   **Output:**
    -   `messages` (List of Text): A list where each element is a JSON string representing a consumed message (e.g., `{"key": "some_key", "value": "some_value"}`).

## Error Handling

The smart services will throw a `SmartServiceException` if errors occur (e.g., configuration issues, Kafka connection problems, invalid inputs). These exceptions can be caught and handled in your Appian process models.

## Development Notes

-   **Plugin Key:** The current plugin key is `com.example.appian.kafka`. Ensure this is unique and update it in `KafkaConfig.java` and `plugin.xml` if necessary.
-   **Dependencies:** All necessary dependencies are listed in the `pom.xml` file.
-   **Logging:** The plugin uses Log4j for logging. Check Appian server logs for detailed information during execution. 