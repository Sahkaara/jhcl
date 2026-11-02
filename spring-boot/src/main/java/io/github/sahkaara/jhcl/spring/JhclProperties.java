package io.github.sahkaara.jhcl.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

/**
 * Configuration properties for the jhcl Spring Boot integration.
 *
 * <p>Example usage in {@code application.yml}:
 * <pre>{@code
 * jhcl:
 *   path: config/app.hcl
 * }</pre>
 *
 * <p>When {@code jhcl.path} is set, {@link JhclAutoConfiguration} creates
 * an {@code HclDocument} bean by parsing the file.
 */
@ConfigurationProperties(prefix = "jhcl")
public class JhclProperties {

    /**
     * Path to the HCL file to parse. When unset, no {@code HclDocument}
     * bean is created.
     */
    private Path path;

    public Path getPath() {
        return path;
    }

    public void setPath(Path path) {
        this.path = path;
    }
}
