package org.gradle.external.junit;

import org.gradle.api.testing.execution.AbstractTestFrameworkDetector;

import java.io.*;
import java.util.List;

/**
 * @author Tom Eyckmans
 */
public class JUnitDetector extends AbstractTestFrameworkDetector<JUnitTestClassDetecter> {
    public JUnitDetector(File testClassesDirectory, List<File> testClasspath) {
        super(testClassesDirectory, testClasspath);
    }

    protected JUnitTestClassDetecter createClassVisitor() {
        return new JUnitTestClassDetecter(this);
    }

}
