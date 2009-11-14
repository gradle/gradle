package org.gradle.external.junit;

/**
 * @author Tom Eyckmans
 */
public class JUnitTestSuiteWrappingTestClassHandler extends AbstractJUnitTestClassHandler {

    public JUnitTestSuiteWrappingTestClassHandler(Class testClass) {
        super(new junit.framework.TestSuite(testClass));
    }

}
