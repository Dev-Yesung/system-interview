package lab.ysng.snowflake_id_generator.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@EnableConfigurationProperties(SnowflakeProperties.class)
@Configuration
public class SnowflakeConfig {
}
