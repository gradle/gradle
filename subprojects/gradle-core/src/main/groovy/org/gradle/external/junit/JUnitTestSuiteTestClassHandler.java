package org.gradle.external.junit;

import junit.framework.Test;

import java.lang.reflect.Method;

/**
 * @author Tom Eyckmans
 */
public class JUnitTestSuiteTestClassHandler extends AbstractJUnitTestClassHandler {

    public JUnitTestSuiteTestClassHandler(Method suiteMethod) {
        try {
            suite = ((Test) suiteMethod.invoke(null));
        }
        catch ( Throwable t ) {
            throw new RuntimeException("failed to create junit test suite", t);
        }
    }
}
