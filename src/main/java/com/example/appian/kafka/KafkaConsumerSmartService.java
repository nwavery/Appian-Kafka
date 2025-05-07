package com.example.appian.kafka;

import com.appiancorp.services.ServiceContext;
import com.appiancorp.suiteapi.common.Name;
import com.appiancorp.suiteapi.process.framework.AppianSmartService;
import com.appiancorp.suiteapi.process.framework.Input;
import com.appiancorp.suiteapi.process.framework.MessageContainer;
import com.appiancorp.suiteapi.process.framework.Required;
import com.appiancorp.suiteapi.process.framework.SmartServiceException;
import com.appiancorp.suiteapi.process.palette.PaletteInfo;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.log4j.Logger;
import com.google.gson.JsonObject; // Using Gson for JSON creation

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
        if (consumerProperties.getProperty(org.apache.kafka.clients.consumer.ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG) == null) {
            throw new SmartServiceException.Builder("Kafka bootstrap servers not configured. Please check plugin configuration.").build();
        }

        List<String> receivedMessages = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProperties)) {
            consumer.subscribe(Collections.singletonList(topic));

            LOG.info("Polling Kafka topic " + topic + " for " + pollTimeoutMs + " ms with group ID " + groupId);
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(pollTimeoutMs));
            LOG.info("Poll finished. Found " + records.count() + " records.");

            for (ConsumerRecord<String, String> record : records) {
                JsonObject messageJson = new JsonObject();
                messageJson.addProperty("key", record.key());
                messageJson.addProperty("value", record.value());
                receivedMessages.add(messageJson.toString());
                LOG.debug("Consumed record: key=" + record.key() + " value=" + record.value() + " topic=" + record.topic() + " partition=" + record.partition() + " offset=" + record.offset());
            }
        } catch (Exception e) {
            LOG.error("Error consuming messages from Kafka topic " + topic, e);
            throw new SmartServiceException.Builder("Failed to consume messages from Kafka: " + e.getMessage()).setCause(e).build();
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
    public void onSave(MessageContainer messages) {
    }

    @Override
    public void validate(MessageContainer messages) {
    }
} 