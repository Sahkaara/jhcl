package io.github.sahkaara.jhcl.spring;

import io.github.sahkaara.jhcl.core.HclDocument;
import io.github.sahkaara.jhcl.core.HclValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JhclAutoConfigurationTest {

    @TempDir
    Path tempDir;

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JhclAutoConfiguration.class));

    @Test
    void noDocumentWhenPathUnset() {
        runner.run(ctx -> assertThat(ctx).doesNotHaveBean(HclDocument.class));
    }

    @Test
    void parsesFileWhenPathIsSet() throws Exception {
        Path file = tempDir.resolve("config.hcl");
        Files.writeString(file, """
                variable "region" {
                  default = "eu-west-1"
                }
                """);

        runner.withPropertyValues("jhcl.path=" + file)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(HclDocument.class);
                    HclDocument doc = ctx.getBean(HclDocument.class);
                    assertThat(doc.hasErrors()).isFalse();
                    assertThat(doc.getString("variable.region.default"))
                            .hasValue("eu-west-1");
                });
    }

    @Test
    void userProvidedBeanWins() throws Exception {
        Path file = tempDir.resolve("ignored.hcl");
        Files.writeString(file, "a = 1\n");

        HclDocument custom = HclDocument.of(
                Map.of("custom", HclValue.of("yes")),
                List.of());

        runner.withPropertyValues("jhcl.path=" + file)
                .withBean(HclDocument.class, () -> custom)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(HclDocument.class);
                    assertThat(ctx.getBean(HclDocument.class).getString("custom"))
                            .hasValue("yes");
                });
    }
}
