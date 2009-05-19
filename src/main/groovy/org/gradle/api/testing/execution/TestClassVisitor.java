package org.gradle.api.testing.execution;

import org.objectweb.asm.commons.EmptyVisitor;
import org.gradle.api.testing.TestFrameworkDetector;

/**
 * @author Tom Eyckmans
 */
public abstract class TestClassVisitor extends EmptyVisitor {

    protected final TestFrameworkDetector detector;

    protected TestClassVisitor(TestFrameworkDetector detector) {
        this.detector = detector;
    }

    public abstract String getClassName();

    public abstract boolean isTest();

    public abstract boolean isAbstract();

    public abstract String getSuperClassName();
}
