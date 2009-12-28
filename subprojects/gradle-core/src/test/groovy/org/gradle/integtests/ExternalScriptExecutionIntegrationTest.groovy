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
        createExternalJar()
        createBuildSrc()

        testFile('external.gradle') << '''
buildscript {
    dependencies { classpath files('repo/test-1.3.jar') }
}
new org.gradle.test.BuildClass()
new BuildSrcClass()
println 'quiet message'
captureStandardOutput(LogLevel.ERROR)
println 'error message'
assertNotNull(project)
assertSame(buildscript.classLoader, getClass().classLoader.parent)
assertSame(buildscript.classLoader, Thread.currentThread().contextClassLoader)
assertSame(gradle.scriptClassLoader, buildscript.classLoader.parent)
assertNotSame(project.buildscript.classLoader, buildscript.classLoader)
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
    public void canExecuteExternalScriptFromSettingsScript() {
        testFile('settings.gradle') << ''' apply { script 'other.gradle' } '''
        testFile('other.gradle') << ''' include 'child' '''
        testFile('build.gradle') << ''' assertEquals(['child'], subprojects*.name) '''

        inTestDirectory().withTaskList().run()
    }

    @Test
    public void canExecuteExternalScriptFromInitScript() {
        TestFile initScript = testFile('init.gradle') << ''' apply { script 'other.gradle' } '''
        testFile('other.gradle') << '''
addListener(new ListenerImpl())
class ListenerImpl extends BuildAdapter {
    public void projectsEvaluated(Gradle gradle) {
        gradle.rootProject.task('doStuff')
    }
}
'''
        inTestDirectory().usingInitScript(initScript).withTasks('doStuff').run()
    }

    @Test
    public void canExecuteExternalScriptFromExternalScript() {
        testFile('build.gradle') << ''' apply { script 'other1.gradle' } '''
        testFile('other1.gradle') << ''' apply { script 'other2.gradle' } '''
        testFile('other2.gradle') << ''' task doStuff '''

        inTestDirectory().withTasks('doStuff').run()
    }

    @Test
    public void canExecuteExternalScriptAgainstAnArbitraryObject() {
        createBuildSrc()

        testFile('external.gradle') << '''
println 'quiet message'
captureStandardOutput(LogLevel.ERROR)
println 'error message'
new BuildSrcClass()
assertEquals('doStuff', name)
assertSame(buildscript.classLoader, getClass().classLoader.parent)
assertSame(buildscript.classLoader, Thread.currentThread().contextClassLoader)
assertSame(project.gradle.scriptClassLoader, buildscript.classLoader.parent)
assertNotSame(project.buildscript.classLoader, buildscript.classLoader)
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

        ExecutionResult result = inTestDirectory().withTasks('doStuff').run()
        assertThat(result.output, containsString('quiet message'))
        assertThat(result.output, not(containsString('error message')))
        assertThat(result.error, containsString('error message'))
        assertThat(result.error, not(containsString('quiet message')))
    }

    private TestFile createBuildSrc() {
        return testFile('buildSrc/src/main/java/BuildSrcClass.java') << '''
            public class BuildSrcClass { }
'''
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
