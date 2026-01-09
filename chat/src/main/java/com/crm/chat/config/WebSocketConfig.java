package com.crm.chat.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

/**
 * WebSocket Configuration for Chat and WebRTC Signaling
 * Optimized for real-time messaging and video call signaling
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * TaskScheduler Bean - Required for WebSocket heartbeat in Spring Boot 4.0+
     * This prevents the "Heartbeat values configured but no TaskScheduler provided" error
     */
    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("websocket-heartbeat-");
        scheduler.initialize();
        return scheduler;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Enable simple in-memory message broker
        // Destinations: /topic (broadcast), /queue (point-to-point), /user (user-specific)
        config.enableSimpleBroker("/topic", "/queue", "/user")
                .setHeartbeatValue(new long[]{10000, 10000}) // Send heartbeat every 10 seconds
                .setTaskScheduler(taskScheduler()); // CRITICAL: Provide TaskScheduler

        // Application destination prefix (messages from client to server)
        config.setApplicationDestinationPrefixes("/app");

        // User destination prefix (for user-specific messages)
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // STOMP endpoint with SockJS fallback
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*") // Allow all origins (configure for production)
                .withSockJS()
                .setStreamBytesLimit(512 * 1024) // 512 KB
                .setHttpMessageCacheSize(1000)
                .setDisconnectDelay(30 * 1000); // 30 seconds

        // STOMP endpoint without SockJS (for modern browsers)
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*");
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        // Optimize for WebRTC signaling (large SDP messages)
        registration
                .setMessageSizeLimit(512 * 1024)        // 512 KB per message
                .setSendBufferSizeLimit(1024 * 1024)    // 1 MB send buffer
                .setSendTimeLimit(20 * 1000);           // 20 seconds send timeout
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // Configure inbound channel (client -> server)
        registration.taskExecutor()
                .corePoolSize(4)
                .maxPoolSize(8)
                .queueCapacity(100);
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        // Configure outbound channel (server -> client)
        registration.taskExecutor()
                .corePoolSize(4)
                .maxPoolSize(8)
                .queueCapacity(100);
    }
}