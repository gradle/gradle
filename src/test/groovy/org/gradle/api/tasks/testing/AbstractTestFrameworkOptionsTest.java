package org.gradle.api.tasks.testing;

import org.gradle.util.JUnit4GroovyMockery;
import org.gradle.api.testing.TestFramework;
import org.jmock.lib.legacy.ClassImposteriser;

/**
 * @author Tom Eyckmans
 */
public class AbstractTestFrameworkOptionsTest<T extends TestFramework> {
    protected JUnit4GroovyMockery context = new JUnit4GroovyMockery();

    protected T testFrameworkMock;

    protected void setUp(Class<T> testFrameworkClass) throws Exception
    {
        context.setImposteriser(ClassImposteriser.INSTANCE);

        testFrameworkMock = context.mock(testFrameworkClass);
    }
}
