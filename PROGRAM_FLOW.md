## Appian-Kafka Connector: Program Flow

The plugin facilitates communication between Appian and a Kafka cluster. It does this through two custom smart services that can be used within Appian process models.

### I. Plugin Loading and Configuration (One-time Setup & On Appian Start)

1.  **Appian Starts/Plugin Deployed:**
    *   When Appian starts up, or when you deploy the plugin JAR file:
        *   Appian reads the `plugin.xml` file (located in `src/main/resources/plugin.xml` within the JAR).
        *   It registers the smart services defined: `KafkaProducerSmartService` and `KafkaConsumerSmartService`. This makes them available in the Appian Process Modeler.
        *   It also reads the `<configuration>` section of `plugin.xml`. These are the global settings for the plugin.

2.  **Administrator Configures Plugin (via Appian Admin Console):**
    *   An Appian administrator navigates to the Admin Console -> Plug-ins.
    *   They select this "Appian Kafka Connector" plugin.
    *   They configure the properties defined in `plugin.xml`, primarily:
        *   `kafka.bootstrap.servers`: The crucial comma-separated list of Kafka broker addresses (e.g., `your-kafka-broker1:9092,your-kafka-broker2:9092`).
        *   `kafka.security.protocol`: (Optional) The security protocol for connecting to Kafka (e.g., `PLAINTEXT`, `SSL`, `SASL_SSL`).
    *   These settings are saved by Appian.

### II. `KafkaProducerSmartService` Flow (When "Produce Kafka Message" is executed in an Appian Process)

1.  **Appian Process Reaches the Smart Service:**
    *   An Appian process model instance executes the "Produce Kafka Message" smart service.
    *   The process model provides inputs to the smart service:
        *   `topic` (Text): The target Kafka topic.
        *   `key` (Text, Optional): The message key.
        *   `value` (Text): The message payload.

2.  **Smart Service Instantiation (`KafkaProducerSmartService.java`):**
    *   Appian instantiates the `com.example.appian.kafka.KafkaProducerSmartService` class.
    *   The constructor `KafkaProducerSmartService(ServiceContext sc)` is called.
        *   It receives a `ServiceContext` (`sc`), which provides access to Appian services and configurations.
        *   It creates a new `KafkaConfig(sc)` object.

3.  **Kafka Configuration Loading (`KafkaConfig.java`):**
    *   The `KafkaConfig` constructor is executed:
        *   It uses `ConfigurationLoader.getConfiguration(sc, PLUGIN_KEY)` to fetch the plugin-specific configurations (like `kafka.bootstrap.servers`, `kafka.security.protocol`) that the administrator saved via the Admin Console. `PLUGIN_KEY` is `com.example.appian.kafka`.
        *   It stores these base properties (bootstrap servers, security protocol) in a `Properties` object called `baseProperties`.

4.  **Producer Properties Preparation (`KafkaProducerSmartService.java` -> `KafkaConfig.java`):**
    *   Back in `KafkaProducerSmartService`, within the `run()` method:
        *   Input validation occurs (topic and value are checked).
        *   `kafkaConfig.getProducerProperties()` is called.
            *   This method in `KafkaConfig.java` takes the `baseProperties` (bootstrap servers, etc.).
            *   It adds producer-specific properties:
                *   `ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG`: Set to `StringSerializer.class.getName()`.
                *   `ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG`: Set to `StringSerializer.class.getName()`.
            *   It returns the complete `Properties` object for the Kafka producer.

5.  **Message Production (`KafkaProducerSmartService.java`):**
    *   A `KafkaProducer<String, String>` is created using the prepared producer properties.
    *   A `ProducerRecord<String, String>` is created using the `topic`, `key`, and `value` inputs.
    *   The `producer.send(record)` method is called to send the message to Kafka.
        *   The code uses `.get()` on the `Future` returned by `send()`. This makes the send operation synchronous, meaning the smart service waits for an acknowledgment (or error) from Kafka.
    *   **If successful:**
        *   The callback logs success information (topic, partition, offset).
        *   The `success` output variable of the smart service is set to `true`.
    *   **If an error occurs** (e.g., Kafka unreachable, serialization error):
        *   An error is logged.
        *   A `SmartServiceException` is created and thrown, which Appian can then handle in the process model (e.g., route to an error flow). The `success` variable remains `false` (its default).
    *   The Kafka producer is automatically closed (due to the try-with-resources block).

6.  **Smart Service Completion:**
    *   The `KafkaProducerSmartService` finishes execution.
    *   The Appian process model continues, now having access to the `success` output variable.

### III. `KafkaConsumerSmartService` Flow (When "Consume Kafka Messages" is executed in an Appian Process)

1.  **Appian Process Reaches the Smart Service:**
    *   An Appian process model instance executes the "Consume Kafka Messages" smart service.
    *   The process model provides inputs:
        *   `topic` (Text): The Kafka topic to consume from.
        *   `groupId` (Text): The consumer group ID.
        *   `pollTimeout` (Number - Integer): How long (in milliseconds) to wait for messages.

2.  **Smart Service Instantiation (`KafkaConsumerSmartService.java`):**
    *   Appian instantiates `com.example.appian.kafka.KafkaConsumerSmartService`.
    *   The constructor `KafkaConsumerSmartService(ServiceContext sc)` is called.
        *   Similar to the producer, it gets a `ServiceContext` and creates a `KafkaConfig(sc)` object.

3.  **Kafka Configuration Loading (`KafkaConfig.java`):**
    *   This step is identical to step II.3: The `KafkaConfig` object loads the global Kafka connection properties from the plugin configuration.

4.  **Consumer Properties Preparation (`KafkaConsumerSmartService.java` -> `KafkaConfig.java`):**
    *   Back in `KafkaConsumerSmartService`, within the `run()` method:
        *   Input validation occurs (topic, groupId, pollTimeoutMs).
        *   `kafkaConfig.getConsumerProperties(groupId)` is called.
            *   This method in `KafkaConfig.java` takes the `baseProperties`.
            *   It adds consumer-specific properties:
                *   `ConsumerConfig.GROUP_ID_CONFIG`: Set to the `groupId` input.
                *   `ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG`: Set to `StringDeserializer.class.getName()`.
                *   `ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG`: Set to `StringDeserializer.class.getName()`.
                *   `ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG`: Set to `"true"` (as per requirements, for automatic offset committing).
                *   `ConsumerConfig.AUTO_OFFSET_RESET_CONFIG`: Set to `"earliest"` (to read from the beginning if no offset is found for the group).
            *   It returns the complete `Properties` object for the Kafka consumer.

5.  **Message Consumption (`KafkaConsumerSmartService.java`):**
    *   A `KafkaConsumer<String, String>` is created using the prepared consumer properties.
    *   The consumer `subscribe()`s to the specified `topic` (using `Collections.singletonList(topic)`).
    *   The `consumer.poll(Duration.ofMillis(pollTimeoutMs))` method is called.
        *   This method attempts to fetch records from the Kafka topic for the consumer group.
        *   It waits for up to `pollTimeoutMs` milliseconds if no messages are immediately available.
    *   `ConsumerRecords<String, String> records` are returned by the `poll()` call.
    *   The code iterates through each `ConsumerRecord` in `records`:
        *   For each record, a `JsonObject` (from the Gson library) is created.
        *   The `record.key()` and `record.value()` are added to this JSON object.
        *   The JSON object is converted to a String (`messageJson.toString()`) and added to a list called `receivedMessages`.
        *   Debug information about the consumed record is logged.
    *   **If an error occurs** during consumption (e.g., Kafka connection issue, deserialization error):
        *   An error is logged.
        *   A `SmartServiceException` is created and thrown.
    *   The Kafka consumer is automatically closed (try-with-resources).
    *   The `receivedMessages` list (of JSON strings) is converted to a `String[]` and set as the `messages` output variable of the smart service.

6.  **Smart Service Completion:**
    *   The `KafkaConsumerSmartService` finishes.
    *   The Appian process model continues, with the `messages` output variable containing an array of JSON strings (each representing a consumed Kafka message), or an empty array if no messages were polled within the timeout. 