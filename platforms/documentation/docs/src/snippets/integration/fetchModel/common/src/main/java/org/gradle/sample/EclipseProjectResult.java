package org.gradle.sample;

import org.gradle.tooling.Failure;
import org.gradle.tooling.model.eclipse.EclipseProject;

import java.io.Serializable;
import java.util.Collection;

public class EclipseProjectResult implements Serializable {
    private final EclipseProject model;
    private final Collection<? extends Failure> failures;

    public EclipseProjectResult(EclipseProject model, Collection<? extends Failure> failures) {
        this.model = model;
        this.failures = failures;
    }

    public EclipseProject getModel() {
        return model;
    }

    public Collection<? extends Failure> getFailures() {
        return failures;
    }
}
