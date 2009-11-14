package org.gradle.external.junit;

import java.lang.reflect.Method;

/**
 * @author Tom Eyckmans
 */
public class JUnitTestClassHandlerFactory {

    public JUnitTestClassHandler createTestClassHandler(final Class testClass)
    {
        JUnitTestClassHandler testClassHandler = null;

        try {
            final Method suiteMethod = testClass.getMethod("suite");

            testClassHandler = new JUnitTestSuiteTestClassHandler(suiteMethod);
        }
        catch (NoSuchMethodException noSuiteMethodException) {
            // test class is not a suite
            if (JUnit4Detecter.isJUnit4Available()) {
                testClassHandler = new JUnit4AdaptingTestClassHandler(testClass);
            }
            else {
                testClassHandler = new JUnitTestSuiteWrappingTestClassHandler(testClass);
            }
        }

        return testClassHandler;
    }

}
