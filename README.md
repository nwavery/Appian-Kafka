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
- **Appian Environment:** A running Appian instance to deploy and test the plugin.
- **Apache Kafka Cluster:** A running Kafka cluster (this plugin now uses Kafka client version 4.0.0).
- **Confluent Schema Registry (or compatible):** Required for Avro message serialization and deserialization. The URL of the Schema Registry must be configured in the plugin settings. (This plugin uses Confluent client libraries version 7.9.0).
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
    This command will compile the code, using Kafka clients 4.0.0 and Confluent libraries 7.9.0 (including Avro schema handling), and package the plugin into a JAR file located in the `target/` directory (e.g., `Connector-0.0.1-SNAPSHOT.jar`).

## Plugin Configuration

The primary configuration for the Kafka connection is managed through the `plugin.xml` file (located in `src/main/resources/plugin.xml`). When the plugin is deployed to Appian, an administrator can configure these settings via the Admin Console.

Key configurable properties:
-   `kafka.bootstrap.servers`: Your Kafka brokers (e.g., `your_broker1:9092,your_broker2:9092`). The plugin is built against Kafka client 4.0.0.
-   `kafka.security.protocol`: (e.g., `SASL_SSL`).
-   `kafka.sasl.mechanism`: (e.g., `PLAIN`).
-   `kafka.schema.registry.url`: URL for your Confluent Schema Registry (e.g., `http://your_schema_registry:8081`). The Avro serializers are from Confluent Platform 7.9.0 libraries.

Refer to the `plugin.xml` file for more details on available configuration properties. The `KafkaConfig.java` class reads these properties.

## Connecting to Confluent Cloud

To connect this plugin to a Kafka cluster hosted on Confluent Cloud, you need to configure specific authentication and encryption settings. Confluent Cloud typically uses API keys with SASL/SSL.

### 1. Obtain Confluent Cloud Kafka Cluster Details:

From your Confluent Cloud dashboard, gather the following:

*   **Bootstrap Server(s):** The address for your Kafka cluster (e.g., `pkc-xxxxx.region.provider.confluent.cloud:9092`).
*   **API Key and Secret:** Generate an API key and secret. The API Key will serve as the SASL username, and the API Secret as the SASL password.

### 2. Required Kafka Client Properties for Confluent Cloud:

When configuring the plugin in the Appian Admin Console, you will use the following settings:

*   `kafka.bootstrap.servers`: Your Confluent Cloud bootstrap server address.
*   `kafka.security.protocol`: Set to `SASL_SSL`.
*   `kafka.sasl.mechanism`: Set to `PLAIN`.
*   `kafka.sasl.jaas.config`: This string provides the credentials and must be formatted as:
    ```
    org.apache.kafka.common.security.plain.PlainLoginModule required username="<YOUR_CONFLUENT_CLOUD_API_KEY>" password="<YOUR_CONFLUENT_CLOUD_API_SECRET>";
    ```
    Replace `<YOUR_CONFLUENT_CLOUD_API_KEY>` and `<YOUR_CONFLUENT_CLOUD_API_SECRET>` with your actual values. The `plugin.xml` has been updated to include fields for these SASL settings, and `KafkaConfig.java` has been modified to read and apply them.

### 3. SSL Truststore Considerations:

Confluent Cloud uses SSL/TLS encryption. The JVM running your Appian instance must trust the Certificate Authority (CA) that signed Confluent Cloud's SSL certificates. Modern JVMs usually include common public CAs in their default truststore, so this often works without extra configuration.

If you encounter SSL handshake errors, you may need to import Confluent Cloud's CA certificate into your Appian JVM's truststore. Consult Confluent Cloud documentation for their CA certificate and Java's `keytool` documentation for instructions on importing certificates.

### 4. Configuration in Appian Admin Console:

After deploying the plugin (with the updated code for SASL support):

1.  Go to the Appian Admin Console -> Plug-ins.
2.  Select the "Appian Kafka Connector" plugin.
3.  Configure the properties as follows:
    *   **Kafka Bootstrap Servers:** Your Confluent Cloud bootstrap server address.
    *   **Kafka Security Protocol:** `SASL_SSL`
    *   **Kafka SASL Mechanism:** `PLAIN`
    *   **Kafka SASL JAAS Configuration:** The formatted JAAS string with your API key and secret (e.g., `org.apache.kafka.common.security.plain.PlainLoginModule required username="API_KEY" password="API_SECRET";`). This field is masked in the Admin Console for security.
    *   **Kafka Schema Registry URL:** The URL for your Confluent Cloud Schema Registry. You will also need to configure API key and secret for Schema Registry access, typically by embedding them in the `schema.registry.url` or by setting `basic.auth.user.info` and `basic.auth.credentials.source` properties for the Schema Registry client. The current implementation passes the main `kafka.schema.registry.url` to the serializers/deserializers. For authenticated Schema Registry access, you might need to adjust `KafkaConfig.java` to set additional properties like `basic.auth.user.info` (for SR API key:secret) and `basic.auth.credentials.source` (to `USER_INFO`) on the producer/consumer properties map if the Schema Registry URL itself doesn't embed credentials. For simplicity, this example assumes the Schema Registry URL is either unsecured or credentials are part of the URL if supported by your SR client version (Confluent libraries 7.9.0).

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
-   **Description:** Sends an Avro-serialized message to a Kafka topic.
-   **Inputs:**
    -   `topic` (Text, Required): The Kafka topic.
    -   `key` (Text, Optional): The message key (still String serialized).
    -   `value` (Text, Required): A **JSON string** representing the data for the Avro record.
    -   `valueSchema` (Text, Required): The **Avro schema string** (in JSON format, i.e., the content of an `.avsc` file) that defines the structure of the `value`.
-   **Output:**
    -   `success` (Boolean): `true` if successful.

### 2. KafkaConsumerSmartService

-   **Display Name:** Consume Kafka Messages
-   **Description:** Consumes Avro messages from a Kafka topic.
-   **Inputs:**
    -   `topic` (Text, Required): The Kafka topic to subscribe to.
    -   `groupId` (Text, Required): The consumer group ID for the Kafka consumer.
    -   `pollTimeout` (Number - Integer, Required): Duration in milliseconds to poll for messages.
-   **Output:**
    -   `messages` (List of Text): A list where each element is a **JSON string representing the consumed Avro record**.

## Error Handling

The smart services will throw a `SmartServiceException` if errors occur (e.g., configuration issues, Kafka connection problems, invalid inputs). These exceptions can be caught and handled in your Appian process models.

## Development Notes

-   **Plugin Key:** The current plugin key is `com.example.appian.kafka`. Ensure this is unique and update it in `KafkaConfig.java` and `plugin.xml` if necessary.
-   **Dependencies:** All necessary dependencies are listed in the `pom.xml` file.
-   **Logging:** The plugin uses Log4j for logging. Check Appian server logs for detailed information during execution.
-   **Avro Schemas:** The producer requires the Avro schema to be provided as a string input. The consumer deserializes Avro messages and converts them to JSON strings. Ensure your Schema Registry is running and accessible.
-   **Dependencies:** Ensure all new dependencies for Avro and Confluent Schema Registry are correctly resolved by Maven (after local Appian SDK install).
 