package com.example.appian.kafka;

import com.appiancorp.services.ServiceContext;
import com.appiancorp.suiteapi.common.Name;
import com.appiancorp.suiteapi.process.framework.AppianSmartService;
import com.appiancorp.suiteapi.process.framework.Input;
import com.appiancorp.suiteapi.process.framework.MessageContainer;
import com.appiancorp.suiteapi.process.framework.Required;
import com.appiancorp.suiteapi.process.framework.SmartServiceException;
import com.appiancorp.suiteapi.process.palette.PaletteInfo;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.log4j.Logger;

import java.util.Properties;

@PaletteInfo(paletteCategory = "Integration Services", palette = "Kafka Messaging")
public class KafkaProducerSmartService extends AppianSmartService {

    private static final Logger LOG = Logger.getLogger(KafkaProducerSmartService.class);
    private final ServiceContext sc;
    private KafkaConfig kafkaConfig;

    private String topic;
    private String key;
    private String value;
    private boolean success;

    public KafkaProducerSmartService(ServiceContext sc) {
        super();
        this.sc = sc;
        this.kafkaConfig = new KafkaConfig(sc);
    }

    @Override
    public void run() throws SmartServiceException {
        if (topic == null || topic.trim().isEmpty()) {
            throw new SmartServiceException.Builder("Topic cannot be null or empty").build();
        }
        if (value == null) { // Key can be null, but value typically shouldn't be for most use cases
            throw new SmartServiceException.Builder("Value cannot be null").build();
        }

        Properties producerProperties = kafkaConfig.getProducerProperties();
        if (producerProperties.getProperty(org.apache.kafka.clients.producer.ProducerConfig.BOOTSTRAP_SERVERS_CONFIG) == null) {
            throw new SmartServiceException.Builder("Kafka bootstrap servers not configured. Please check plugin configuration.").build();
        }

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProperties)) {
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, value);
            producer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    LOG.error("Error sending message to Kafka topic " + topic, exception);
                    // success remains false
                    // SmartServiceException cannot be thrown directly from callback in async send
                    // Consider how to signal failure back to Appian: perhaps set a flag and check after send?
                    // For simplicity as per prompt, we'll rely on the synchronous nature of send().get() or handle error logging.
                } else {
                    LOG.info("Message sent successfully to topic: " + metadata.topic() + ", partition: " + metadata.partition() + ", offset: " + metadata.offset());
                    this.success = true;
                }
            }).get(); // Using .get() to make it synchronous and catch exceptions immediately

        } catch (Exception e) {
            LOG.error("Failed to send message to Kafka topic " + topic, e);
            throw new SmartServiceException.Builder("Failed to send message to Kafka: " + e.getMessage()).setCause(e).build();
        }
    }

    @Input(required = Required.ALWAYS)
    @Name("topic")
    public void setTopic(String topic) {
        this.topic = topic;
    }

    @Input(required = Required.OPTIONAL) // Assuming key can be optional
    @Name("key")
    public void setKey(String key) {
        this.key = key;
    }

    @Input(required = Required.ALWAYS)
    @Name("value")
    public void setValue(String value) {
        this.value = value;
    }

    @Name("success")
    public boolean isSuccess() {
        return success;
    }

    // Optional: If you need to do any cleanup or resource release after the service execution
    @Override
    public void onSave(MessageContainer messages) {
    }

    // Optional: If you need to do any validation before the service execution
    @Override
    public void validate(MessageContainer messages) {
    }
} 