package org.gradle;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class NestedTestsWithTags {

    @Tag("org.gradle.SomeCategory")
    public static class TagOnClass {
        @ParameterizedTest
        @ValueSource(strings = {"tag on class"})
        public void run(String param) {
            System.err.println("executed " + param);
        }
    }

    public static class TagOnMethod {
        @ParameterizedTest
        @ValueSource(strings = {"tag on method"})
        @Tag("org.gradle.SomeCategory")
        public void run(String param) {
            System.err.println("executed " + param);
        }

        @Test
        public void filteredOut() {
            throw new AssertionError("should be filtered out");
        }
    }

    public static class TagOnMethodNoParam {
        @Test
        @Tag("org.gradle.SomeCategory")
        public void run() {
            System.err.println("executed tag on method (no param)");
        }

        @Test
        public void filteredOut() {
            throw new AssertionError("should be filtered out");
        }
    }

    public static class Untagged {
        @ParameterizedTest
        @ValueSource(strings = {"untagged"})
        public void run(String param) {
            System.err.println("executed " + param);
        }
    }
}
