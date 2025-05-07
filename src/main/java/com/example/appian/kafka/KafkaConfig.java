package com.example.appian.kafka;

import java.util.Properties;
import com.appiancorp.services.ServiceContext;
import com.appiancorp.suiteapi.cfg.Configuration;
import com.appiancorp.suiteapi.cfg.ConfigurationLoader;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

public class KafkaConfig {

    private static final String PLUGIN_KEY = "com.example.appian.kafka"; // Replace with your actual plugin key
    private static final String BOOTSTRAP_SERVERS_CONFIG = "kafka.bootstrap.servers";
    private static final String SECURITY_PROTOCOL_CONFIG = "kafka.security.protocol";
    private static final String SASL_MECHANISM_CONFIG = "kafka.sasl.mechanism";
    private static final String SASL_JAAS_CONFIG = "kafka.sasl.jaas.config";
    private static final String SCHEMA_REGISTRY_URL_CONFIG = "kafka.schema.registry.url";
    // Add other Kafka property keys as needed, e.g., SASL mechanism, JAAS config

    private Properties baseProperties;
    private String schemaRegistryUrl;

    public KafkaConfig(ServiceContext sc) {
        this.baseProperties = new Properties();
        try {
            Configuration config = ConfigurationLoader.getConfiguration(sc, PLUGIN_KEY);
            String bootstrapServers = config.getString(BOOTSTRAP_SERVERS_CONFIG);
            if (bootstrapServers == null || bootstrapServers.trim().isEmpty()) {
                // Consider throwing an exception or logging a severe error if bootstrap servers are not configured
                System.err.println("Kafka bootstrap servers are not configured in plugin.xml");
            }
            baseProperties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            baseProperties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

            this.schemaRegistryUrl = config.getString(SCHEMA_REGISTRY_URL_CONFIG);
            // It's good practice to check if schemaRegistryUrl is null or empty if Avro is intended to be used, 
            // but serializers will fail later if it's missing and they are configured.

            String securityProtocol = config.getString(SECURITY_PROTOCOL_CONFIG);
            if (securityProtocol != null && !securityProtocol.trim().isEmpty()) {
                baseProperties.put("security.protocol", securityProtocol);
                // If SASL is used, load mechanism and JAAS config
                if (securityProtocol.startsWith("SASL")) {
                    String saslMechanism = config.getString(SASL_MECHANISM_CONFIG);
                    if (saslMechanism != null && !saslMechanism.trim().isEmpty()) {
                        baseProperties.put("sasl.mechanism", saslMechanism);
                    }
                    String saslJaasConfig = config.getString(SASL_JAAS_CONFIG);
                    if (saslJaasConfig != null && !saslJaasConfig.trim().isEmpty()) {
                        baseProperties.put("sasl.jaas.config", saslJaasConfig);
                    }
                }
            }

        } catch (Exception e) {
            // Handle exceptions during configuration loading, e.g., log the error
            // For now, just printing stack trace, but proper logging/exception handling is needed
            e.printStackTrace();
        }
    }

    public Properties getProducerProperties() {
        Properties props = new Properties();
        props.putAll(baseProperties);
        // Using KafkaAvroSerializer for values
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, io.confluent.kafka.serializers.KafkaAvroSerializer.class.getName());
        if (this.schemaRegistryUrl != null && !this.schemaRegistryUrl.trim().isEmpty()) {
            props.put("schema.registry.url", this.schemaRegistryUrl);
        }
        // Add any other producer-specific properties here, e.g., for Avro behavior if needed:
        // props.put(io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig.AUTO_REGISTER_SCHEMAS, true); // Usually true by default
        return props;
    }

    public Properties getConsumerProperties(String groupId) {
        Properties props = new Properties();
        props.putAll(baseProperties);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        // Using KafkaAvroDeserializer for values
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, io.confluent.kafka.serializers.KafkaAvroDeserializer.class.getName());
        if (this.schemaRegistryUrl != null && !this.schemaRegistryUrl.trim().isEmpty()) {
            props.put("schema.registry.url", this.schemaRegistryUrl);
        }
        // For Avro consumer, you might want to ensure it returns GenericRecord if not using specific Avro classes
        // props.put(io.confluent.kafka.serializers.KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, false); // false is default

        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"); // Or "latest" depending on requirements
        // Add any other consumer-specific properties here
        return props;
    }
} 