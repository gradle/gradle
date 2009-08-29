package org.gradle.integtests

import org.junit.Test
import static org.junit.Assert.*
import static org.hamcrest.Matchers.*

class BuildScriptExecutionIntegrationTest extends AbstractIntegrationTest {

    @Test
    public void executesBuildScriptWithCorrectEnvironment() {
        testFile('build.gradle') << '''
println 'quiet message'
captureStandardOutput(LogLevel.ERROR)
println 'error message'
assertNotNull(project)
assertSame(Thread.currentThread().contextClassLoader, buildscript.classLoader)

task doStuff
'''

        ExecutionResult result = inTestDirectory().withTasks('doStuff').run()
        assertThat(result.output, containsString('quiet message'))
        assertThat(result.output, not(containsString('error message')))
        assertThat(result.error, containsString('error message'))
        assertThat(result.error, not(containsString('quiet message')))
    }
}