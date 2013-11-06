package org.gradle.api.internal.tasks.options;

import org.gradle.api.GradleException;

public class OptionValidationException extends GradleException {
    public OptionValidationException(String message) {
        super(message);
    }
}
