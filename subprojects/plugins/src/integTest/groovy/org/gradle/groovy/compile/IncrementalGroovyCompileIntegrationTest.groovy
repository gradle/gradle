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
package org.gradle.groovy.compile

import org.gradle.integtests.fixtures.AbstractIntegrationTest
import org.gradle.integtests.fixtures.TestResources
import org.gradle.integtests.fixtures.executer.ExecutionFailure
import org.junit.Rule
import org.junit.Test

class IncrementalGroovyCompileIntegrationTest extends AbstractIntegrationTest {

    @Rule public final TestResources resources = new TestResources(testDirectoryProvider)

    @Test
    public void recompilesSourceWhenPropertiesChange() {
        executer.withTasks('compileGroovy').run().assertTasksSkipped(':compileJava')

        file('build.gradle').text += '''
            compileGroovy.options.debug = false
'''

        executer.withTasks('compileGroovy').run().assertTasksSkipped(':compileJava')

        executer.withTasks('compileGroovy').run().assertTasksSkipped(':compileJava', ':compileGroovy')
    }

    @Test
    public void recompilesDependentClasses() {
        executer.withTasks("classes").run();

        // Update interface, compile should fail
        file('src/main/groovy/IPerson.groovy').assertIsFile().copyFrom(file('NewIPerson.groovy'))

        ExecutionFailure failure = executer.withTasks("classes").runWithFailure();
        failure.assertHasDescription("Execution failed for task ':compileGroovy'.");
    }
}
