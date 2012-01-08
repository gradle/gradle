package org.gradle.api.internal.tasks.testing.results;

import org.gradle.api.tasks.testing.TestDescriptor;

/**
 * by Szczepan Faber, created at: 1/8/12
 */
public class UnknownTestDescriptor implements TestDescriptor {

    public String getName() {
        return "Unknown test (possible bug, please report)";
    }

    public String getClassName() {
        return null;
    }

    public boolean isComposite() {
        return false;
    }

    public TestDescriptor getParent() {
        return null;
    }
}