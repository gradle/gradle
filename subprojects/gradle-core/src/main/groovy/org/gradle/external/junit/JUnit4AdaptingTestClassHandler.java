package org.gradle.external.junit;

/**
 * @author Tom Eyckmans
 */
public class JUnit4AdaptingTestClassHandler extends AbstractJUnitTestClassHandler {

    public JUnit4AdaptingTestClassHandler(Class testClass) {
        super(new junit.framework.JUnit4TestAdapter(testClass));
    }
}
