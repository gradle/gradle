package org.gradle;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;

import static org.junit.Assert.fail;

public class NestedTestsWithCategories {

    @Category(SomeCategory.class)
    @RunWith(Parameterized.class)
    public static class TagOnClass {
        @Parameterized.Parameters
        public static Iterable<Object[]> getParameters() {
            ArrayList<Object[]> parameters = new ArrayList<>();
            parameters.add(new Object[] { "tag on class" });
            return parameters;
        }

        private final String param;

        public TagOnClass(String param) {
            this.param = param;
        }

        @Test
        public void run() {
            System.err.println("executed " + param);
        }
    }

    @RunWith(Parameterized.class)
    public static class TagOnMethod {
        @Parameterized.Parameters
        public static Iterable<Object[]> getParameters() {
            ArrayList<Object[]> parameters = new ArrayList<>();
            parameters.add(new Object[] { "tag on method" });
            return parameters;
        }

        private final String param;

        public TagOnMethod(String param) {
            this.param = param;
        }

        @Test
        @Category(SomeCategory.class)
        public void run() {
            System.err.println("executed " + param);
        }

        @Test
        public void filteredOut() {
            throw new AssertionError("should be filtered out");
        }
    }

    public static class TagOnMethodNoParam {
        @Test
        @Category(SomeCategory.class)
        public void run() {
            System.err.println("executed tag on method (no param)");
        }

        @Test
        public void filteredOut() {
            throw new AssertionError("should be filtered out");
        }
    }

    @RunWith(Parameterized.class)
    public static class Untagged {
        @Parameterized.Parameters
        public static Iterable<Object[]> getParameters() {
            ArrayList<Object[]> parameters = new ArrayList<>();
            parameters.add(new Object[] { "untagged" });
            return parameters;
        }

        private final String param;

        public Untagged(String param) {
            this.param = param;
        }

        @Test
        public void run() {
            System.err.println("executed " + param);
        }
    }
}
