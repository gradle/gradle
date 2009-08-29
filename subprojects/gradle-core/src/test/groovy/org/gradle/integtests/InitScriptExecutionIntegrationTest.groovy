package org.gradle.integtests

import org.junit.Test
import static org.junit.Assert.*
import static org.hamcrest.Matchers.*

class InitScriptExecutionIntegrationTest extends AbstractIntegrationTest {
    @Test
    public void executesSettingsScriptWithCorrectEnvironment() {
        TestFile initScript = testFile('init.gradle')
        initScript << '''
println 'quiet message'
captureStandardOutput(LogLevel.ERROR)
println 'error message'
assertNotNull(gradle)
assertSame(Thread.currentThread().contextClassLoader, gradle.initscript.classLoader)
'''
        testFile('build.gradle') << 'task doStuff'

        ExecutionResult result = inTestDirectory().usingInitScript(initScript).withTasks('doStuff').run()
        assertThat(result.output, containsString('quiet message'))
        assertThat(result.output, not(containsString('error message')))
        assertThat(result.error, containsString('error message'))
        assertThat(result.error, not(containsString('quiet message')))
    }
}