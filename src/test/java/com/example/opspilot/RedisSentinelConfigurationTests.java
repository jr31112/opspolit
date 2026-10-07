package com.example.opspilot;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "REDIS_SENTINEL_MASTER=test-master",
        "REDIS_SENTINEL_1=localhost:26379",
        "REDIS_SENTINEL_2=localhost:26380",
        "REDIS_SENTINEL_3=localhost:26381",
        "REDIS_SENTINEL_USERNAME=sentinel-user",
        "REDIS_SENTINEL_PASSWORD=sentinel-test-password",
        "REDIS_USERNAME=data-user",
        "REDIS_PASSWORD=data-test-password"
})
@ActiveProfiles("test")
class RedisSentinelConfigurationTests {
    @Autowired LettuceConnectionFactory connectionFactory;

    @Test
    void selectsSentinelAndSeparatesServerAndSentinelCredentials() {
        var config = connectionFactory.getSentinelConfiguration();
        assertThat(config).isNotNull();
        assertThat(config.getMaster().getName()).isEqualTo("test-master");
        assertThat(config.getSentinels()).hasSize(3);
        assertThat(config.getSentinels()).extracting(node -> node.getPort())
                .containsExactlyInAnyOrder(26379, 26380, 26381);
        assertThat(config.getUsername()).isEqualTo("data-user");
        assertThat(config.getPassword().get()).isEqualTo("data-test-password".toCharArray());
        assertThat(config.getSentinelUsername()).isEqualTo("sentinel-user");
        assertThat(config.getSentinelPassword().get()).isEqualTo("sentinel-test-password".toCharArray());
    }
}
