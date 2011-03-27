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

import org.gradle.integtests.fixtures.ArtifactBuilder
import org.gradle.integtests.fixtures.ExecutionResult
import org.gradle.util.TestFile
import org.junit.Test
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*
import org.gradle.integtests.fixtures.internal.AbstractIntegrationTest

class InitScriptExecutionIntegrationTest extends AbstractIntegrationTest {
    @Test
    public void executesInitScriptWithCorrectEnvironment() {
        createExternalJar();

        TestFile initScript = testFile('init.gradle')
        initScript << '''
initscript {
    dependencies { classpath files('repo/test-1.3.jar') }
}
new org.gradle.test.BuildClass()
println 'quiet message'
captureStandardOutput(LogLevel.ERROR)
println 'error message'
assert gradle != null
assert initscript.classLoader == getClass().classLoader.parent
assert initscript.classLoader == Thread.currentThread().contextClassLoader
assert scriptClassLoader == initscript.classLoader.parent
assert Gradle.class.classLoader == scriptClassLoader.parent.parent
'''
        testFile('build.gradle') << 'task doStuff'

        ExecutionResult result = inTestDirectory().usingInitScript(initScript).withTasks('doStuff').run()
        assertThat(result.output, containsString('quiet message'))
        assertThat(result.output, not(containsString('error message')))
        assertThat(result.error, containsString('error message'))
        assertThat(result.error, not(containsString('quiet message')))
    }

    @Test
    public void eachScriptHasIndependentClassLoader() {
        createExternalJar()

        TestFile initScript1 = testFile('init1.gradle')
        initScript1 << '''
initscript {
    dependencies { classpath files('repo/test-1.3.jar') }
}
new org.gradle.test.BuildClass()
'''
        TestFile initScript2 = testFile('init2.gradle')
        initScript2 << '''
try {
    Class.forName('org.gradle.test.BuildClass')
    fail()
} catch (ClassNotFoundException e) {
}
'''

        testFile('build.gradle') << 'task doStuff'

       inTestDirectory().usingInitScript(initScript1).usingInitScript(initScript2)
    }

    private def createExternalJar() {
        ArtifactBuilder builder = artifactBuilder();
        builder.sourceFile('org/gradle/test/BuildClass.java') << '''
            package org.gradle.test;
            public class BuildClass { }
'''
        builder.buildJar(testFile("repo/test-1.3.jar"))
    }
}
