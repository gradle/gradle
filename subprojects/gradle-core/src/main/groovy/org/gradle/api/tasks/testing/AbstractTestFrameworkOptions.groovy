package org.gradle.api.tasks.testing

import org.gradle.api.tasks.compile.AbstractOptions
import org.gradle.api.GradleException

/**
 * @author Tom Eyckmans
 */

public abstract class AbstractTestFrameworkOptions extends AbstractOptions {
    protected AbstractTestFramework testFramework;

    protected AbstractTestFrameworkOptions(AbstractTestFramework testFramework) {
        if ( testFramework == null ) throw new IllegalArgumentException("testFramework == null!")
        
        this.testFramework = testFramework;
    }

    public def propertyMissing(String name) {
        throw new GradleException(
            """
            Property ${name} could not be found in the options of the ${testFramework.name} test framework.

            ${AbstractTestFramework.USE_OF_CORRECT_TEST_FRAMEWORK}
            """);
    }

    public def methodMissing(String name, args) {
        throw new GradleException(
            """
            Method ${name} could not be found in the options of the ${testFramework.name} test framework.

            ${AbstractTestFramework.USE_OF_CORRECT_TEST_FRAMEWORK}
            """);
    }
}
