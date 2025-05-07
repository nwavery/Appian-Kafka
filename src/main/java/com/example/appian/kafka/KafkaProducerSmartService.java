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
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.log4j.Logger;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.io.Decoder;
import org.apache.avro.io.DecoderFactory;

import java.io.IOException;
import java.util.Properties;

@PaletteInfo(paletteCategory = "Integration Services", palette = "Kafka Messaging")
public class KafkaProducerSmartService extends AppianSmartService {

    private static final Logger LOG = Logger.getLogger(KafkaProducerSmartService.class);
    private final ServiceContext sc;
    private KafkaConfig kafkaConfig;

    private String topic;
    private String key;
    private String value;
    private String valueSchemaString;
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
        if (value == null) {
            throw new SmartServiceException.Builder("Value (JSON string for Avro record) cannot be null").build();
        }
        if (valueSchemaString == null || valueSchemaString.trim().isEmpty()) {
            throw new SmartServiceException.Builder("Value Schema (Avro schema string) cannot be null or empty").build();
        }

        Properties producerProperties = kafkaConfig.getProducerProperties();
        if (producerProperties.getProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG) == null) {
            throw new SmartServiceException.Builder("Kafka bootstrap servers not configured. Please check plugin configuration.").build();
        }
        if (producerProperties.getProperty("schema.registry.url") == null || producerProperties.getProperty("schema.registry.url").trim().isEmpty()){
            LOG.warn("Schema Registry URL is not configured. KafkaAvroSerializer might fail if not already cached or if schema auto-registration is off elsewhere.");
        }

        Schema schema;
        try {
            schema = new Schema.Parser().parse(valueSchemaString);
        } catch (Exception e) {
            LOG.error("Failed to parse Avro schema string for topic " + topic, e);
            throw new SmartServiceException.Builder("Invalid Avro schema provided: " + e.getMessage()).setCause(e).build();
        }

        GenericRecord avroRecord;
        try {
            DecoderFactory decoderFactory = DecoderFactory.get();
            Decoder decoder = decoderFactory.jsonDecoder(schema, value);
            GenericDatumReader<GenericRecord> reader = new GenericDatumReader<>(schema);
            avroRecord = reader.read(null, decoder);
        } catch (IOException e) {
            LOG.error("Failed to convert JSON value to Avro GenericRecord for topic " + topic + " using schema: " + schema.getFullName(), e);
            throw new SmartServiceException.Builder("Failed to convert JSON to Avro: " + e.getMessage() + ". Ensure JSON matches schema.").setCause(e).build();
        }

        try (KafkaProducer<String, Object> producer = new KafkaProducer<>(producerProperties)) {
            ProducerRecord<String, Object> record = new ProducerRecord<>(topic, key, avroRecord);
            producer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    LOG.error("Error sending Avro message to Kafka topic " + topic, exception);
                    // success remains false
                } else {
                    LOG.info("Avro Message sent successfully to topic: " + metadata.topic() + ", partition: " + metadata.partition() + ", offset: " + metadata.offset());
                    this.success = true;
                }
            }).get(); // Using .get() to make it synchronous

        } catch (Exception e) {
            LOG.error("Failed to send Avro message to Kafka topic " + topic, e);
            throw new SmartServiceException.Builder("Failed to send Avro message to Kafka: " + e.getMessage()).setCause(e).build();
        }
    }

    @Input(required = Required.ALWAYS)
    @Name("topic")
    public void setTopic(String topic) {
        this.topic = topic;
    }

    @Input(required = Required.OPTIONAL)
    @Name("key")
    public void setKey(String key) {
        this.key = key;
    }

    @Input(required = Required.ALWAYS)
    @Name("value")
    public void setValue(String value) {
        this.value = value;
    }

    @Input(required = Required.ALWAYS)
    @Name("valueSchema")
    public void setValueSchemaString(String valueSchemaString) {
        this.valueSchemaString = valueSchemaString;
    }

    @Name("success")
    public boolean isSuccess() {
        return success;
    }

    @Override
    public void onSave(MessageContainer messages) {}

    @Override
    public void validate(MessageContainer messages) {}
} 