/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.integtests.fixtures.executer.ExecutionFailure
import org.gradle.util.TestFile
import org.junit.Test
import static org.hamcrest.Matchers.*
import org.gradle.integtests.fixtures.AbstractIntegrationTest

class ExternalScriptErrorIntegrationTest extends AbstractIntegrationTest {
    @Test
    public void reportsScriptEvaluationFailsWithGroovyException() {
        testFile('build.gradle') << '''
apply { from 'other.gradle' }
'''
        TestFile script = testFile('other.gradle') << '''

doStuff()
'''

        ExecutionFailure failure = inTestDirectory().runWithFailure()

        failure.assertHasFileName("Script '${script}'");
        failure.assertHasLineNumber(3);
        failure.assertHasDescription('A problem occurred evaluating script.');
        failure.assertHasCause('Could not find method doStuff() for arguments [] on root project');
    }

    @Test
    public void reportsScriptCompilationException() {
        testFile('build.gradle') << '''
apply { from 'other.gradle' }
'''
        TestFile script = testFile('other.gradle')
        script.text = 'import org.gradle()'

        ExecutionFailure failure = inTestDirectory().runWithFailure()
        failure.assertHasFileName("Script '${script}'");
        failure.assertHasLineNumber(1);
        failure.assertHasDescription("Could not compile script '${script}'");
        failure.assertThatCause(containsString("script '${script}': 1: unexpected token: ("))
    }

    @Test
    public void reportsMissingScript() {
        TestFile buildScript = testFile('build.gradle') << '''
apply { from 'unknown.gradle' }
'''
        TestFile script = testFile('unknown.gradle')

        ExecutionFailure failure = inTestDirectory().runWithFailure()
        failure.assertHasFileName("Build file '${buildScript}");
        failure.assertHasLineNumber(2);
        failure.assertHasDescription("A problem occurred evaluating root project");
        failure.assertHasCause("Could not read script '${script}' as it does not exist.");
    }

    @Test
    public void reportsTaskExecutionFailsWithRuntimeException() {
        testFile('build.gradle') << '''
apply { from 'other.gradle' }
'''
        TestFile script = testFile('other.gradle') << '''
task doStuff << {
    throw new RuntimeException('fail')
}
'''

        ExecutionFailure failure = inTestDirectory().withTasks('doStuff').runWithFailure()

        failure.assertHasFileName("Script '${script}'");
        failure.assertHasLineNumber(3);
        failure.assertHasDescription('Execution failed for task \':doStuff\'');
        failure.assertHasCause('fail');
    }

}

