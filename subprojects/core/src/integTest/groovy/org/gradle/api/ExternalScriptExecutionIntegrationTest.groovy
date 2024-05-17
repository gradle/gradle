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


package org.gradle.api

import org.gradle.integtests.fixtures.AbstractIntegrationTest
import org.gradle.integtests.fixtures.executer.ArtifactBuilder
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.server.http.HttpServer
import org.gradle.test.matchers.UserAgentMatcher
import org.gradle.util.GradleVersion
import org.junit.Rule
import org.junit.Test

@SuppressWarnings("IntegrationTestFixtures")
class ExternalScriptExecutionIntegrationTest extends AbstractIntegrationTest {
    @Rule
    public final HttpServer server = new HttpServer()

    @Test
    void executesExternalScriptAgainstAProjectWithCorrectEnvironment() {
        createExternalJar()
        createBuildSrc()

        def implClassName = 'com.google.common.collect.Multimap'
        TestFile externalScript = testFile('external.gradle')
        externalScript << """
buildscript {
    dependencies { classpath files('repo/test-1.3.jar') }
}
new org.gradle.test.BuildClass()
new BuildSrcClass()
println 'quiet message'
logging.captureStandardOutput(LogLevel.ERROR)
println 'error message'
assert project != null
assert "${externalScript.absolutePath.replace("\\", "\\\\")}" == buildscript.sourceFile as String
assert "${externalScript.toURI()}" == buildscript.sourceURI as String
assert buildscript.classLoader == getClass().classLoader.parent
assert buildscript.classLoader == Thread.currentThread().contextClassLoader
assert project.buildscript.classLoader != buildscript.classLoader
Gradle.class.classLoader.loadClass('${implClassName}')
try {
    buildscript.classLoader.loadClass('${implClassName}')
    assert false: 'should fail'
} catch (ClassNotFoundException e) {
    // expected
} finally {
    if (buildscript.classLoader instanceof Closeable) {
        buildscript.classLoader.close()
    }
}

task doStuff
ext.someProp = 'value'
"""
        testFile('build.gradle') << '''
apply { from 'external.gradle' }
assert 'value' == someProp
'''

        ExecutionResult result = inTestDirectory().withTasks('doStuff').run()
        result.assertOutputContains('quiet message')
        result.assertHasErrorOutput('error message')
    }

    @Test
    void canExecuteExternalScriptAgainstAnArbitraryObject() {
        createBuildSrc()

        testFile('external.gradle') << '''
println 'quiet message'
getLogging().captureStandardOutput(LogLevel.ERROR)
println 'error message'
new BuildSrcClass()
assert 'doStuff' == name
assert buildscript.classLoader == getClass().classLoader.parent
assert buildscript.classLoader == Thread.currentThread().contextClassLoader
ext.someProp = 'value'
'''
        testFile('build.gradle') << '''
task doStuff
apply {
    to doStuff
    from 'external.gradle'
}
assert 'value' == doStuff.someProp
'''

        ExecutionResult result = inTestDirectory().withTasks('doStuff').run()
        result.assertOutputContains('quiet message')
        result.assertHasErrorOutput('error message')
    }

    @Test
    void canExecuteExternalScriptFromSettingsScript() {
        testFile('settings.gradle') << ''' apply { from 'other.gradle' } '''
        createDirs("child")
        testFile('other.gradle') << ''' include 'child' '''
        testFile('build.gradle') << ''' assert ['child'] == subprojects*.name '''

        inTestDirectory().withTasks("help").run()
    }

    @Test
    void canExecuteExternalScriptFromInitScript() {
        TestFile initScript = testFile('init.gradle') << ''' apply { from 'other.gradle' } '''
        testFile('other.gradle') << '''
            projectsEvaluated {
                gradle.rootProject.task('doStuff')
            }
        '''
        inTestDirectory().usingInitScript(initScript).withTasks('doStuff').run()
    }

    @Test
    void canExecuteExternalScriptFromExternalScript() {
        testFile('build.gradle') << ''' apply { from 'other1.gradle' } '''
        testFile('other1.gradle') << ''' apply { from 'other2.gradle' } '''
        testFile('other2.gradle') << ''' task doStuff '''

        inTestDirectory().withTasks('doStuff').run()
    }

    @Test
    void canFetchScriptViaHttp() {
        executer.requireOwnGradleUserHomeDir() //we need an empty external resource cache

        TestFile script = testFile('external.gradle')
        server.expectUserAgent(UserAgentMatcher.matchesNameAndVersion("Gradle", GradleVersion.current().getVersion()))
        server.expectGet('/external.gradle', script)
        server.start()

        script << """
            task doStuff
            assert buildscript.sourceFile == null
            assert "${server.uri}/external.gradle" == buildscript.sourceURI as String
"""

        testFile('build.gradle') << """
            apply from: '${server.uri}/external.gradle'
            defaultTasks 'doStuff'
"""

        inTestDirectory().run()
    }

    @Test
    void cachesScriptClassForAGivenScript() {
        createDirs("a", "b")
        testFile('settings.gradle') << 'include \'a\', \'b\''
        testFile('external.gradle') << 'ext.appliedScript = this'
        testFile('build.gradle') << '''
allprojects {
   apply from: "$rootDir/external.gradle"
}
subprojects {
    assert appliedScript.class == rootProject.appliedScript.class
}
task doStuff
'''
        inTestDirectory().withTasks('doStuff').run()
    }

    private TestFile createBuildSrc() {
        return testFile('buildSrc/src/main/java/BuildSrcClass.java') << '''
            public class BuildSrcClass { }
'''
    }

    private def createExternalJar() {
        ArtifactBuilder builder = artifactBuilder()
        builder.sourceFile('org/gradle/test/BuildClass.java') << '''
            package org.gradle.test;
            public class BuildClass { }
'''
        builder.buildJar(testFile("repo/test-1.3.jar"))
    }
}
