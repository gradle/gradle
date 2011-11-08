package org.gradle.api.internal.artifacts.ivyservice;

import org.gradle.api.GradleException;

public class ModuleResolveException extends GradleException {
    public ModuleResolveException(String message) {
        super(message);
    }

    public ModuleResolveException(String message, Throwable cause) {
        super(message, cause);
    }
}
