package org.gradle.api.internal.tasks.testing.failure.mappers;

import junit.framework.ComparisonFailure;

public class CustomComparisonFailure extends ComparisonFailure {

    /**
     * Constructs a comparison failure.
     *
     * @param message  the identifying message or null
     * @param expected the expected string value
     * @param actual   the actual string value
     */
    public CustomComparisonFailure(String message, String expected, String actual) {
        super(message, expected, actual);
    }
}
