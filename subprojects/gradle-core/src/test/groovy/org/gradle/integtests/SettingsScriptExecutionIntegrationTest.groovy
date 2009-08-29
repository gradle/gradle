package org.gradle.integtests

import org.junit.Test
import static org.junit.Assert.*
import static org.hamcrest.Matchers.*

class SettingsScriptExecutionIntegrationTest extends AbstractIntegrationTest {
    @Test
    public void executesSettingsScriptWithCorrectEnvironment() {
        testFile('settings.gradle') << '''
println 'quiet message'
captureStandardOutput(LogLevel.ERROR)
println 'error message'
assertNotNull(settings)
assertSame(Thread.currentThread().contextClassLoader, classLoader)
'''
        testFile('build.gradle') << 'task doStuff'

        ExecutionResult result = inTestDirectory().withTasks('doStuff').run()
        assertThat(result.output, containsString('quiet message'))
        assertThat(result.output, not(containsString('error message')))
        assertThat(result.error, containsString('error message'))
        assertThat(result.error, not(containsString('quiet message')))
    }
}