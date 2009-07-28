package org.gradle.api.tasks.testing;

import org.jmock.Expectations;
import org.jmock.lib.legacy.ClassImposteriser;
import org.gradle.api.Project;
import org.gradle.util.JUnit4GroovyMockery;

import java.io.File;
import java.util.List;
import java.util.Arrays;

/**
 * @author Tom Eyckmans
 */
public abstract class AbstractTestFrameworkTest {

    protected JUnit4GroovyMockery context = new JUnit4GroovyMockery();

    protected Project projectMock;
    protected Test testMock;

    protected final File projectDir = new File("projectDir");
    protected final File testClassesDir = new File("testClassesDir");
    protected final List<File> testSrcDirs = Arrays.asList(new File("testSrcDirs"));
    protected final File testResultsDir = new File("testResultsDir");
    protected final File testReportDir = new File("testReportDir");

    protected void setUp() throws Exception
    {
        context.setImposteriser(ClassImposteriser.INSTANCE);

        projectMock = context.mock(Project.class);
        testMock = context.mock(Test.class);
    }

    protected void expectHandleEmptyIncludesExcludes()
    {
        context.checking(new Expectations(){{
            one(testMock).getIncludes(); will(returnValue(null));
            one(testMock).include("**/*Tests.class", "**/*Test.class");
            one(testMock).getExcludes(); will(returnValue(null));
            one(testMock).exclude("**/Abstract*.class");
        }});
    }
}
