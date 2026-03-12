package com.aldeamo.messaging.workerconsumer.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Value("${app.rabbitmq.exchange}")
    private String exchange;

    @Value("${app.rabbitmq.queue}")
    private String queue;

    @Value("${app.rabbitmq.routing-key}")
    private String routingKey;

    @Value("${app.rabbitmq.retry-queue}")
    private String retryQueue;

    @Value("${app.rabbitmq.retry-routing-key}")
    private String retryRoutingKey;

    @Value("${app.rabbitmq.dlq-queue}")
    private String dlqQueue;

    @Value("${app.rabbitmq.dlq-routing-key}")
    private String dlqRoutingKey;

    @Value("${app.rabbitmq.retry-ttl}")
    private int retryTtl;

    @Bean
    public DirectExchange messagingExchange() {
        return new DirectExchange(exchange, true, false);
    }

    @Bean
    public Queue messagingQueue() {
        return QueueBuilder.durable(queue)
                .withArgument("x-dead-letter-exchange", exchange)
                .withArgument("x-dead-letter-routing-key", retryRoutingKey)
                .build();
    }

    @Bean
    public Queue retryQueue() {
        return QueueBuilder.durable(retryQueue)
                .withArgument("x-dead-letter-exchange", exchange)
                .withArgument("x-dead-letter-routing-key", routingKey)
                .withArgument("x-message-ttl", retryTtl)
                .build();
    }

    @Bean
    public Queue dlqQueue() {
        return QueueBuilder.durable(dlqQueue).build();
    }

    @Bean
    public Binding messagingBinding() {
        return BindingBuilder.bind(messagingQueue()).to(messagingExchange()).with(routingKey);
    }

    @Bean
    public Binding retryBinding() {
        return BindingBuilder.bind(retryQueue()).to(messagingExchange()).with(retryRoutingKey);
    }

    @Bean
    public Binding dlqBinding() {
        return BindingBuilder.bind(dlqQueue()).to(messagingExchange()).with(dlqRoutingKey);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter());
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setPrefetchCount(10);
        return factory;
    }
}
