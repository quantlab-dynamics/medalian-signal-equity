package com.quantlab.signal.service.redisService;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.quantlab.signal.dto.redisDto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class RedisConfig {
    // Generic method to create RedisTemplate with Jackson2Json serializer
    private static final Logger logger = LoggerFactory.getLogger(RedisConfig.class);

    // Generic method to create RedisTemplate with Jackson2Json serializer
    private <T> RedisTemplate<String, T> createJsonRedisTemplate(RedisConnectionFactory redisConnectionFactory,
                                                                 Class<T> type) {
        RedisTemplate<String, T> template = new RedisTemplate<>();
        try {
            template.setConnectionFactory(redisConnectionFactory);

            // Use Jackson2JsonRedisSerializer for value serialization
            Jackson2JsonRedisSerializer<T> jsonSerializer = new Jackson2JsonRedisSerializer<>(type);
            template.setKeySerializer(new StringRedisSerializer());
            template.setValueSerializer(jsonSerializer);
        } catch (Exception e) {
            logger.error("Error creating RedisTemplate with Jackson2JsonRedisSerializer for type: " + type.getName(),
                    e);
            throw new RuntimeException("Failed to create RedisTemplate for type: " + type.getName(), e);
        }

        return template;
    }

    // Generic method to create RedisTemplate with Kryo serializer for binary data
    private <T> RedisTemplate<String, T> createKryoRedisTemplate(RedisConnectionFactory redisConnectionFactory,
                                                                 Class<T> type) {
        RedisTemplate<String, T> template = new RedisTemplate<>();
        try {
            template.setConnectionFactory(redisConnectionFactory);

            KryoRedisSerializer<T> kryoSerializer = new KryoRedisSerializer<>(type);
            template.setKeySerializer(new StringRedisSerializer());
            template.setValueSerializer(kryoSerializer);
        } catch (Exception e) {
            logger.error("Error creating RedisTemplate with KryoRedisSerializer for type: " + type.getName(), e);
            throw new RuntimeException("Failed to create RedisTemplate for type: " + type.getName(), e);
        }

        return template;
    }

    @Bean
    public ChannelTopic strategyTopic() {
        return new ChannelTopic("strategy-check-topic");
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplateForPubSub(
            RedisConnectionFactory redisConnectionFactory,
            ObjectMapper objectMapper) {

        Jackson2JsonRedisSerializer<Object> serializer =
                new Jackson2JsonRedisSerializer<>(objectMapper, Object.class);

        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);
        return template;
    }

    @Bean
    public MessageListenerAdapter messageListenerAdapter(StrategyCheckEventConsumer strategyCheckEventConsumer,
                                                         ObjectMapper objectMapper) {
        Jackson2JsonRedisSerializer<StrategyCheckEvent> serializer =
                new Jackson2JsonRedisSerializer<>(objectMapper, StrategyCheckEvent.class);

        MessageListenerAdapter adapter = new MessageListenerAdapter(strategyCheckEventConsumer, "handleMessage");
        adapter.setSerializer(serializer);
        return adapter;
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory redisConnectionFactory,
            MessageListenerAdapter messageListenerAdapter,
            ChannelTopic strategyTopic) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redisConnectionFactory);
        container.addMessageListener(messageListenerAdapter, strategyTopic);
        return container;
    }


    @Bean
    @Primary
    public RedisTemplate<String, Object> redisTemplateForKeys(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer()); // or use a more generic serializer
        return template;
    }

    @Bean
    public RedisTemplate<String, String> redisTemplateList(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        return template;
    }

    @Bean
    public RedisTemplate<String, Object> pnlRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        return template;
    }

    @Bean
    public RedisTemplate<String, SyntheticPrice> redisTemplateSynthetic(RedisConnectionFactory redisConnectionFactory) {
        try {
            return createJsonRedisTemplate(redisConnectionFactory, SyntheticPrice.class);
        } catch (Exception e) {
            logger.error("Error creating RedisTemplate for MasterResponseFO", e);
            throw new RuntimeException("Failed to create RedisTemplate for MasterResponseFO", e);
        }
    }

//    @Bean
//    public RedisTemplate<String, TouchlineBinaryResponse> redisTemplate6(
//            RedisConnectionFactory redisConnectionFactory) {
//        try {
//            return createJsonRedisTemplate(redisConnectionFactory, TouchlineBinaryResposne.class);
//        } catch (Exception e) {
//            logger.error("Error creating RedisTemplate for TouchlineBinaryResposne", e);
//            throw new RuntimeException("Failed to create RedisTemplate for TouchlineBinaryResposne", e);
//        }
//    }


    @Bean
    public RedisTemplate<String, MasterResponseFO> redisTemplate5(RedisConnectionFactory redisConnectionFactory) {
        try {
            return createJsonRedisTemplate(redisConnectionFactory, MasterResponseFO.class);
        } catch (Exception e) {
            logger.error("Error creating RedisTemplate for MasterResponseFO", e);
            throw new RuntimeException("Failed to create RedisTemplate for MasterResponseFO", e);
        }
    }


    @Bean
    public RedisTemplate<String, InterActiveTokensDTO> redisTemplateInteractiveTokens(RedisConnectionFactory redisConnectionFactory) {
        try {
            return createJsonRedisTemplate(redisConnectionFactory, InterActiveTokensDTO.class);
        } catch (Exception e) {
            logger.error("Error creating RedisTemplate for InterActiveTokensDTO", e);
            throw new RuntimeException("Failed to create RedisTemplate for InterActiveTokensDTO", e);
        }
    }

//    @Bean
//    public RedisTemplate<String, Object> pnlRedisTemplate(RedisConnectionFactory connectionFactory) {
//        RedisTemplate<String, Object> template = new RedisTemplate<>();
//        template.setConnectionFactory(connectionFactory);
//        template.setKeySerializer(new StringRedisSerializer());
//        template.setHashKeySerializer(new StringRedisSerializer());
//        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
//        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
//        return template;
//    }

    // RedisTemplate for OrderDetails using JSON serialization
    @Bean
    public  RedisTemplate<String, OrderDetails> redisTemplateForOrderDetails(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<String, OrderDetails> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory);

        // Jackson2JsonRedisSerializer for OrderDetails
        Jackson2JsonRedisSerializer<OrderDetails> jacksonSerializer = new Jackson2JsonRedisSerializer<>(OrderDetails.class);

        // Use Jackson's ObjectMapper for customization (optional)
        ObjectMapper objectMapper = new ObjectMapper();

        jacksonSerializer.setObjectMapper(objectMapper);

        // Set serializers for both key and value
        template.setKeySerializer(new StringRedisSerializer());  // Use StringRedisSerializer for the key
        template.setValueSerializer(jacksonSerializer);  // Use Jackson2JsonRedisSerializer for the value

        return template;
    }


    @Bean
    public RedisTemplate<String, MarketData> redisTemplate1(
            RedisConnectionFactory redisConnectionFactory) {
        try {
            return createJsonRedisTemplate(redisConnectionFactory, MarketData.class);
        } catch (Exception e) {
            logger.error("Error creating RedisTemplate for TouchlineBinaryResposne", e);
            throw new RuntimeException("Failed to create RedisTemplate for TouchlineBinaryResposne", e);
        }
    }

    @Bean
    public RedisTemplate<String, MarketDepthBinaryResponse> redisTemplate2(
            RedisConnectionFactory redisConnectionFactory) {
        try {
            return createKryoRedisTemplate(redisConnectionFactory, MarketDepthBinaryResponse.class);
        } catch (Exception e) {
            logger.error("Error creating RedisTemplate for MarketDepthBinaryResponse", e);
            throw new RuntimeException("Failed to create RedisTemplate for MarketDepthBinaryResponse", e);
        }
    }

    @Bean
    public RedisTemplate<String, OpenInterestBinaryResponse> redisTemplate3(
            RedisConnectionFactory redisConnectionFactory) {
        try {
            return createKryoRedisTemplate(redisConnectionFactory, OpenInterestBinaryResponse.class);
        } catch (Exception e) {
            logger.error("Error creating RedisTemplate for OpenInterestBinaryResponse", e);
            throw new RuntimeException("Failed to create RedisTemplate for OpenInterestBinaryResponse", e);
        }
    }

    // Custom Kryo Redis serializer class
    public static class KryoRedisSerializer<T> implements RedisSerializer<T> {

        private static final Logger logger = LoggerFactory.getLogger(KryoRedisSerializer.class);
        private final Kryo kryo;
        private final Class<T> type;

        public KryoRedisSerializer(Class<T> type) {
            this.kryo = new Kryo();
            this.type = type;
        }

        @Override
        public byte[] serialize(T t) throws org.springframework.data.redis.serializer.SerializationException {
            try {
                if (t == null) {
                    return new byte[0];
                }
                try (Output output = new Output(256, -1)) {
                    kryo.writeObject(output, t);
                    return output.toBytes();
                }
            } catch (Exception e) {
                logger.error("Error serializing object of type: " + type.getName(), e);
                throw new org.springframework.data.redis.serializer.SerializationException(
                        "Failed to serialize object of type: " + type.getName(), e);
            }
        }

        @Override
        public T deserialize(byte[] bytes) throws org.springframework.data.redis.serializer.SerializationException {
            try {
                if (bytes == null || bytes.length == 0) {
                    return null;
                }
                try (Input input = new Input(bytes)) {
                    return kryo.readObject(input, type);
                }
            } catch (Exception e) {
                logger.error("Error deserializing bytes to object of type: " + type.getName(), e);
                throw new org.springframework.data.redis.serializer.SerializationException(
                        "Failed to deserialize bytes to object of type: " + type.getName(), e);
            }
        }
    }

}


