/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.test.fixtures.hegel;

import dev.hegel.HegelTest;
import org.junit.platform.commons.JUnitException;
import org.junit.platform.commons.support.AnnotationSupport;
import org.junit.platform.engine.FilterResult;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.PostDiscoveryFilter;

/**
 * Selects or drops {@link HegelTest} tests, for Gradle's weekly property-based testing build and for platforms
 * that Hegel has no native library for.
 * <p>
 * The JUnit Platform launcher registers this through {@link java.util.ServiceLoader}, so it applies to every engine
 * on the test runtime classpath. {@code @HegelTest} has no JUnit tag, so tag filters can't do this.
 * The mode comes from the {@value #MODE_PROPERTY} system property, which Gradle's build sets on the test JVM.
 */
public final class HegelTestFilter implements PostDiscoveryFilter {
    /**
     * System property with the {@link Mode}, in lower case. Without it, the mode is {@link Mode#INCLUDE}.
     */
    public static final String MODE_PROPERTY = "org.gradle.test.hegel.filter";

    /**
     * What the filter does.
     */
    public enum Mode {
        /**
         * Keep every test.
         */
        INCLUDE,
        /**
         * Keep only {@link HegelTest} tests.
         */
        ONLY,
        /**
         * Drop {@link HegelTest} tests.
         */
        EXCLUDE
    }

    private final Mode mode;

    /**
     * Creates a filter with the mode from the system property. Used by {@link java.util.ServiceLoader}.
     */
    public HegelTestFilter() {
        this(modeFromSystemProperty());
    }

    public HegelTestFilter(Mode mode) {
        this.mode = mode;
    }

    private static Mode modeFromSystemProperty() {
        String value = System.getProperty(MODE_PROPERTY);
        if (value == null) {
            return Mode.INCLUDE;
        }
        for (Mode mode : Mode.values()) {
            if (mode.name().equalsIgnoreCase(value)) {
                return mode;
            }
        }
        throw new IllegalStateException("System property " + MODE_PROPERTY + " is '" + value + "', but must be one of include, only or exclude");
    }

    @Override
    public FilterResult apply(TestDescriptor descriptor) {
        if (mode == Mode.INCLUDE) {
            return FilterResult.included("all tests are kept");
        }
        boolean hegelTest = isHegelTest(descriptor);
        if (mode == Mode.ONLY) {
            return FilterResult.includedIf(hegelTest, () -> "is a @HegelTest", () -> "is not a @HegelTest");
        }
        return FilterResult.includedIf(!hegelTest, () -> "is not a @HegelTest", () -> "is a @HegelTest");
    }

    private static boolean isHegelTest(TestDescriptor descriptor) {
        TestSource source = descriptor.getSource().orElse(null);
        if (!(source instanceof MethodSource)) {
            return false;
        }
        try {
            return AnnotationSupport.isAnnotated(((MethodSource) source).getJavaMethod(), HegelTest.class);
        } catch (JUnitException | LinkageError e) {
            // Not every engine's method sources resolve to a Java method, e.g. Vintage's parameterized "test[0]"
            return false;
        }
    }
}
