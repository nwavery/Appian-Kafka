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
    // Add other Kafka property keys as needed, e.g., SASL mechanism, JAAS config

    private Properties baseProperties;

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

            String securityProtocol = config.getString(SECURITY_PROTOCOL_CONFIG);
            if (securityProtocol != null && !securityProtocol.trim().isEmpty()) {
                baseProperties.put("security.protocol", securityProtocol);
                // You might need to load other security-related properties here
                // e.g., sasl.mechanism, sasl.jaas.config
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
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        // Add any other producer-specific properties here
        return props;
    }

    public Properties getConsumerProperties(String groupId) {
        Properties props = new Properties();
        props.putAll(baseProperties);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"); // Or "latest" depending on requirements
        // Add any other consumer-specific properties here
        return props;
    }
} 