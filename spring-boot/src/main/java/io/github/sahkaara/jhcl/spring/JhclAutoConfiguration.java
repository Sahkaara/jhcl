package io.github.sahkaara.jhcl.spring;

import io.github.sahkaara.jhcl.core.HclDocument;
import io.github.sahkaara.jhcl.core.Jhcl;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.io.IOException;

/**
 * Spring Boot auto-configuration for jhcl.
 *
 * <p>Activates only when {@code jhcl.path} is set. Registers a single
 * {@link HclDocument} bean parsed from that file. Applications can
 * inject the document directly:
 *
 * <pre>{@code
 * @Service
 * public class ConfigService {
 *     private final HclDocument config;
 *
 *     public ConfigService(HclDocument config) {
 *         this.config = config;
 *     }
 *
 *     public String region() {
 *         return config.getString("variable.region.default").orElse("eu-west-1");
 *     }
 * }
 * }</pre>
 *
 * <p>If the application already defines its own {@code HclDocument}
 * bean, this auto-configuration backs off, letting user code win.
 */
@AutoConfiguration
@EnableConfigurationProperties(JhclProperties.class)
@ConditionalOnProperty(prefix = "jhcl", name = "path")
public class JhclAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public HclDocument hclDocument(JhclProperties props) throws IOException {
        return Jhcl.parse(props.getPath());
    }
}
