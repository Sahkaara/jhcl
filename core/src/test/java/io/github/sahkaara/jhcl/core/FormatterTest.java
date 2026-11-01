package io.github.sahkaara.jhcl.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FormatterTest {

    @Test
    void normalizesIndentationAndSpacing() {
        String messy = "resource \"aws\" \"web\" {\nami=\"x\"\n   count=3\n}\n";
        String want  = "resource \"aws\" \"web\" {\n  ami   = \"x\"\n  count = 3\n}\n";
        assertEquals(want, Formatter.format(messy));
    }

    @Test
    void alignsEqualsInRun() {
        String in  = "a = 1\nlonger = 2\nb = 3\n";
        String out = "a      = 1\nlonger = 2\nb      = 3\n";
        assertEquals(out, Formatter.format(in));
    }

    @Test
    void preservesComments() {
        String in = "a = 1  # note\n# full line\nb = 2\n";
        String out = "a = 1  # note\n# full line\nb = 2\n";
        assertEquals(out, Formatter.format(in));
    }

    @Test
    void idempotent() {
        String messy = "resource \"aws\" \"web\" {\n  ami=\"x\"\n}\n";
        String once  = Formatter.format(messy);
        String twice = Formatter.format(once);
        assertEquals(once, twice);
    }

    @Test
    void emptyInputIsEmptyOutput() {
        assertEquals("", Formatter.format(""));
        assertEquals("", Formatter.format(null));
    }

    @Test
    void formatsSampleIdempotently() {
        String sample = """
            variable "region" {
              default = "eu-west-1"
              type    = string
            }

            resource "aws_instance" "web" {
              ami           = "ami-12345"
              instance_type = "t2.micro"
              count         = 3
            }
            """;
        assertEquals(sample, Formatter.format(sample));
    }
}
