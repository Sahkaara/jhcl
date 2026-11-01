package io.github.sahkaara.jhcl.core;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class JhclTest {

    private static HclDocument doc;

    @BeforeAll
    static void loadSample() throws IOException {
        try (InputStream in = JhclTest.class.getResourceAsStream("/sample.hcl")) {
            assertNotNull(in, "sample.hcl must be on the test classpath");
            String source = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            doc = Jhcl.parse(source);
        }
    }

    @Test
    void parsesSampleFileWithoutErrors() {
        assertFalse(doc.hasErrors(), () -> "unexpected errors: " + doc.errors());
        assertFalse(doc.blocks().isEmpty());
    }

    @Test
    void readsNestedAttributes() {
        assertEquals("eu-west-1", doc.getString("variable.region.default").orElseThrow());
        assertEquals("string",    doc.getString("variable.region.type").orElseThrow());
        assertEquals("ami-12345", doc.getString("resource.aws_instance.web.ami").orElseThrow());
        assertEquals(3,           doc.getInt   ("resource.aws_instance.web.count").orElseThrow());
    }

    @Test
    void readsObjectLiteral() {
        var tags = (HclValue.ObjectValue) doc.get("resource.aws_instance.web.tags").orElseThrow();
        assertEquals("web",  ((HclValue.StringValue) tags.entries().get("Name")).value());
        assertEquals("prod", ((HclValue.StringValue) tags.entries().get("Env")).value());
    }

    @Test
    void readsListLiteral() {
        var sg = (HclValue.ListValue) doc.get("resource.aws_instance.web.security_groups").orElseThrow();
        assertEquals(2, sg.items().size());
        assertEquals("default", ((HclValue.StringValue) sg.items().get(0)).value());
        assertEquals("web",     ((HclValue.StringValue) sg.items().get(1)).value());
    }

    @Test
    void formatIsPassthroughForNow() {
        String src = "a = 1\n";
        assertEquals(src, Jhcl.format(src));
    }

    @Test
    void lintReturnsEmptyForNow() {
        assertTrue(Jhcl.lint(doc).isEmpty());
    }
}
