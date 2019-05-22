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
package org.gradle.api

import org.gradle.api.internal.FeaturePreviewsActivationFixture
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.ArtifactBuilder
import org.gradle.internal.os.OperatingSystem
import org.gradle.test.fixtures.file.TestFile
import spock.lang.Issue
import spock.lang.Unroll

class SettingsScriptExecutionIntegrationTest extends AbstractIntegrationSpec {

    @Unroll
    def "emits deprecation warnings when enabling inactive #feature feature"() {
        given:
        settingsFile << """
            enableFeaturePreview('$feature')
        """

        when:
        executer.expectDeprecationWarning()
        succeeds()

        then:
        outputContains("enableFeaturePreview('$feature') has been deprecated.")
        outputContains("The feature flag is no longer relevant, please remove it from your settings file.")

        where:
        feature << FeaturePreviewsActivationFixture.inactiveFeatures()
    }

    @Issue("https://github.com/gradle/gradle/issues/8840")
    def "can use exec in settings"() {
        addExecToScript(settingsFile)
        when:
        succeeds()
        then:
        outputContains("hello from settings")
    }

    @Issue("https://github.com/gradle/gradle/issues/8840")
    def "can use exec in settings applied from another script"() {
        settingsFile << """
            apply from: 'other.gradle'
        """
        addExecToScript(file("other.gradle"))
        when:
        succeeds()
        then:
        outputContains("hello from settings")
    }

    private void addExecToScript(TestFile scriptFile) {
        file("message") << """
            hello from settings
        """
        if (OperatingSystem.current().windows) {
            scriptFile << """
                exec {
                    commandLine "cmd", "/c", "type", "message"
                }
            """
        } else {
            scriptFile << """
                exec {
                    commandLine "cat", "message"
                }
            """
        }
    }

    def "notices changes to settings scripts that do not change the file length"() {
        settingsFile.text = "println 'counter: __'"
        long before = settingsFile.length()

        expect:
        (10..40).each {
            settingsFile.text = "println 'counter: $it'"
            assert settingsFile.length() == before

            succeeds()
            result.assertOutputContains("counter: $it")
        }
    }

    def "executes settings script with correct environment"() {
        given:
        createExternalJar()
        createBuildSrc()
        def implClassName = 'com.google.common.collect.Multimap'

        settingsFile << """
buildscript {
    dependencies { classpath files('repo/test-1.3.jar') }
}
new org.gradle.test.BuildClass()
new BuildSrcClass();
println 'quiet message'
logging.captureStandardOutput(LogLevel.ERROR)
println 'error message'
assert settings != null
assert buildscript.classLoader == getClass().classLoader.parent
assert buildscript.classLoader == Thread.currentThread().contextClassLoader
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
"""
        buildFile << 'task doStuff'

        when:
        run('doStuff')

        then:
        output.contains('quiet message')
        errorOutput.contains('error message')
    }

    private TestFile createBuildSrc() {
        return file('buildSrc/src/main/java/BuildSrcClass.java') << '''
            public class BuildSrcClass { }
        '''
    }

    private def createExternalJar() {
        ArtifactBuilder builder = artifactBuilder();
        builder.sourceFile('org/gradle/test/BuildClass.java') << '''
            package org.gradle.test;
            public class BuildClass { }
        '''
        builder.buildJar(file("repo/test-1.3.jar"))
    }
}
