/*
 * Copyright 2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.integtests


import static org.junit.Assert.*
import static org.hamcrest.Matchers.*
import org.junit.Test

public class ExternalScriptExecutionIntegrationTest extends AbstractIntegrationTest {
    @Test
    public void executesExternalScriptAgainstAProjectWithCorrectEnvironment() {
        testFile('external.gradle') << '''
println 'quiet message'
captureStandardOutput(LogLevel.ERROR)
println 'error message'
assertNotNull(project)
assertSame(Thread.currentThread().contextClassLoader, buildscript.classLoader)
task doStuff
someProp = 'value'
'''
        testFile('build.gradle') << '''
apply { script 'external.gradle' }
assertEquals('value', someProp)
'''

        ExecutionResult result = inTestDirectory().withTasks('doStuff').run()
        assertThat(result.output, containsString('quiet message'))
        assertThat(result.output, not(containsString('error message')))
        assertThat(result.error, containsString('error message'))
        assertThat(result.error, not(containsString('quiet message')))
    }

    @Test
    public void canExecuteExternalScriptAgainstAnArbitraryObject() {
        testFile('external.gradle') << '''
assertEquals('doStuff', name)
assertSame(Thread.currentThread().contextClassLoader, project.buildscript.classLoader)
someProp = 'value'
'''
        testFile('build.gradle') << '''
task doStuff
apply {
    to doStuff
    script 'external.gradle'
}
assertEquals('value', doStuff.someProp)
'''

        inTestDirectory().withTasks('doStuff').run()
    }
}
