package com.example.appian.kafka;

import com.appiancorp.services.ServiceContext;
import com.appiancorp.suiteapi.common.Name;
import com.appiancorp.suiteapi.process.framework.AppianSmartService;
import com.appiancorp.suiteapi.process.framework.Input;
import com.appiancorp.suiteapi.process.framework.MessageContainer;
import com.appiancorp.suiteapi.process.framework.Required;
import com.appiancorp.suiteapi.process.framework.SmartServiceException;
import com.appiancorp.suiteapi.process.palette.PaletteInfo;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.log4j.Logger;

// Avro Imports
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.io.JsonEncoder;

// Using Gson for JSON creation was for the old string-based message, removing as Avro provides its own JSON conversion.
// import com.google.gson.JsonObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException; // For Avro JSON conversion
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

@PaletteInfo(paletteCategory = "Integration Services", palette = "Kafka Messaging")
public class KafkaConsumerSmartService extends AppianSmartService {

    private static final Logger LOG = Logger.getLogger(KafkaConsumerSmartService.class);
    private final ServiceContext sc;
    private KafkaConfig kafkaConfig;

    private String topic;
    private String groupId;
    private Integer pollTimeoutMs;
    private String[] messages;

    public KafkaConsumerSmartService(ServiceContext sc) {
        super();
        this.sc = sc;
        this.kafkaConfig = new KafkaConfig(sc);
    }

    @Override
    public void run() throws SmartServiceException {
        if (topic == null || topic.trim().isEmpty()) {
            throw new SmartServiceException.Builder("Topic cannot be null or empty").build();
        }
        if (groupId == null || groupId.trim().isEmpty()) {
            throw new SmartServiceException.Builder("GroupId cannot be null or empty").build();
        }
        if (pollTimeoutMs == null || pollTimeoutMs <= 0) {
            throw new SmartServiceException.Builder("Poll Timeout (ms) must be a positive integer").build();
        }

        Properties consumerProperties = kafkaConfig.getConsumerProperties(groupId);
        if (consumerProperties.getProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG) == null) {
            throw new SmartServiceException.Builder("Kafka bootstrap servers not configured. Please check plugin configuration.").build();
        }
        if (consumerProperties.getProperty("schema.registry.url") == null || consumerProperties.getProperty("schema.registry.url").trim().isEmpty()){
            LOG.warn("Schema Registry URL is not configured. KafkaAvroDeserializer might fail.");
            // Depending on strictness, could throw exception here.
        }

        List<String> receivedMessages = new ArrayList<>();
        // KafkaConsumer type changed to <String, GenericRecord>
        try (KafkaConsumer<String, GenericRecord> consumer = new KafkaConsumer<>(consumerProperties)) {
            consumer.subscribe(Collections.singletonList(topic));

            LOG.info("Polling Kafka topic " + topic + " for " + pollTimeoutMs + " ms with group ID " + groupId + " for Avro messages.");
            ConsumerRecords<String, GenericRecord> records = consumer.poll(Duration.ofMillis(pollTimeoutMs));
            LOG.info("Poll finished. Found " + records.count() + " Avro records.");

            for (ConsumerRecord<String, GenericRecord> record : records) {
                GenericRecord avroRecord = record.value();
                if (avroRecord != null) {
                    try {
                        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                        JsonEncoder encoder = EncoderFactory.get().jsonEncoder(avroRecord.getSchema(), outputStream);
                        GenericDatumWriter<GenericRecord> writer = new GenericDatumWriter<>(avroRecord.getSchema());
                        writer.write(avroRecord, encoder);
                        encoder.flush();
                        outputStream.close(); // Good practice, though ByteArrayOutputStream close() is a no-op
                        receivedMessages.add(outputStream.toString("UTF-8"));
                        LOG.debug("Consumed Avro record, converted to JSON: key=" + record.key() + " topic=" + record.topic() + " partition=" + record.partition() + " offset=" + record.offset());
                    } catch (IOException e) {
                        LOG.error("Failed to convert Avro GenericRecord to JSON for record from topic " + topic, e);
                        // Optionally add a placeholder or error message to the list, or skip this record
                        receivedMessages.add("{\"error\": \"Failed to convert Avro record to JSON: " + e.getMessage() + "\"}");
                    }
                } else {
                    LOG.warn("Consumed a null Avro record from topic " + topic + " at offset " + record.offset());
                    receivedMessages.add(null); // Or a representation of null like "null"
                }
            }
        } catch (Exception e) {
            LOG.error("Error consuming Avro messages from Kafka topic " + topic, e);
            throw new SmartServiceException.Builder("Failed to consume Avro messages from Kafka: " + e.getMessage()).setCause(e).build();
        }
        this.messages = receivedMessages.toArray(new String[0]);
    }

    @Input(required = Required.ALWAYS)
    @Name("topic")
    public void setTopic(String topic) {
        this.topic = topic;
    }

    @Input(required = Required.ALWAYS)
    @Name("groupId")
    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    @Input(required = Required.ALWAYS)
    @Name("pollTimeout")
    public void setPollTimeoutMs(Integer pollTimeoutMs) {
        this.pollTimeoutMs = pollTimeoutMs;
    }

    @Name("messages")
    public String[] getMessages() {
        return messages;
    }

    @Override
    public void onSave(MessageContainer messages) {}

    @Override
    public void validate(MessageContainer messages) {}
} 