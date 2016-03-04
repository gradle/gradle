/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.testkit.runner

import org.gradle.integtests.fixtures.executer.ForkingGradleExecuter
import org.gradle.integtests.fixtures.executer.UnderDevelopmentGradleDistribution
import org.gradle.testkit.runner.fixtures.AutomaticClasspathInjectionFixture
import org.gradle.testkit.runner.fixtures.annotations.InjectsPluginClasspath
import org.gradle.testkit.runner.fixtures.annotations.InspectsBuildOutput
import org.gradle.util.GFileUtils
import org.gradle.util.GUtil
import org.gradle.util.UsesNativeServices

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
import static org.gradle.testkit.runner.internal.DefaultGradleRunner.IMPLEMENTATION_CLASSPATH_PROP_KEY
import static org.gradle.testkit.runner.internal.DefaultGradleRunner.PLUGIN_METADATA_FILE_NAME

@InjectsPluginClasspath
@InspectsBuildOutput
@UsesNativeServices
class GradleRunnerAutomaticPluginInjectionIntegrationTest extends GradleRunnerIntegrationTest {

    private final AutomaticClasspathInjectionFixture fixture = new AutomaticClasspathInjectionFixture()
    private final File projectDir = file('sampleProject')

    def setup() {
        compilePluginProjectSources()
        buildFile << """
            plugins {
                id "com.company.helloworld"
            }
        """
    }

    def "injects plugin metadata classpath if requested"() {
        given:
        List<File> pluginClasspath = fixture.getPluginClasspath(projectDir)
        File pluginClasspathFile = fixture.createPluginClasspathManifestFile(projectDir, pluginClasspath)

        when:
        BuildResult result = fixture.withClasspath([pluginClasspathFile.parentFile]) {
            runner('helloWorld')
                .withPluginClasspath()
                .build()
        }

        then:
        result.task(":helloWorld").outcome == SUCCESS
    }

    def "does not automatically inject plugin metadata classpath if not requested and metadata file is located"() {
        given:
        List<File> pluginClasspath = fixture.getPluginClasspath(projectDir)
        File pluginClasspathFile = fixture.createPluginClasspathManifestFile(projectDir, pluginClasspath)

        when:
        BuildResult result = fixture.withClasspath([pluginClasspathFile.parentFile]) {
            runner('helloWorld')
                .buildAndFail()
        }

        then:
        execFailure(result).assertHasDescription("""
            |Plugin [id: 'com.company.helloworld'] was not found in any of the following sources:
            |
            |- Gradle Core Plugins (plugin is not in 'org.gradle' namespace)
            |- Gradle Central Plugin Repository (plugin dependency must include a version number for this source)
        """.stripMargin().trim())
    }

    def "injected plugin metadata classpath can be overridden"() {
        given:
        List<File> pluginClasspath = fixture.getPluginClasspath(projectDir)
        File pluginClasspathFile = fixture.createPluginClasspathManifestFile(projectDir, pluginClasspath)
        List<File> userDefinedClasspath = [projectDir.file('does/not/exist')]

        when:
        BuildResult result = fixture.withClasspath([pluginClasspathFile.parentFile]) {
            runner('helloWorld')
                .withPluginClasspath()
                .withPluginClasspath(userDefinedClasspath)
                .buildAndFail()
        }

        then:
        execFailure(result).assertHasDescription("""
            |Plugin [id: 'com.company.helloworld'] was not found in any of the following sources:
            |
            |- Gradle Core Plugins (plugin is not in 'org.gradle' namespace)
            |- Gradle TestKit (classpath: ${userDefinedClasspath*.absolutePath.join(File.pathSeparator)})
            |- Gradle Central Plugin Repository (plugin dependency must include a version number for this source)
        """.stripMargin().trim())
    }

    def "throws exception if plugin metadata classpath is requested to be injected but cannot locate metadata file"() {
        when:
        runner('helloWorld')
            .withPluginClasspath()
            .buildAndFail()

        then:
        Throwable t = thrown(InvalidPluginMetadataException)
        t.message == "Test runtime classpath does not contain plugin metadata file '$PLUGIN_METADATA_FILE_NAME'"
    }

    def "does not inject plugin metadata classpath if implementation-classpath property is empty"() {
        given:
        File pluginClasspathFile = fixture.createPluginClasspathManifestFile(projectDir, [])

        when:
        BuildResult result = fixture.withClasspath([pluginClasspathFile.parentFile]) {
            runner('helloWorld')
                .withPluginClasspath()
                .buildAndFail()
        }

        then:
        pluginClasspathFile.exists()
        Properties properties = GUtil.loadProperties(pluginClasspathFile)
        !properties.containsKey('implementation-classpath')
        execFailure(result).assertHasDescription("""
            |Plugin [id: 'com.company.helloworld'] was not found in any of the following sources:
            |
            |- Gradle Core Plugins (plugin is not in 'org.gradle' namespace)
            |- Gradle Central Plugin Repository (plugin dependency must include a version number for this source)
        """.stripMargin().trim())
    }

    def "throws exception if plugin metadata does not contain implementation-classpath property"() {
        given:
        File pluginClasspathFile = fixture.createPluginClasspathManifestFile(projectDir, [], ['other': 'prop'])

        when:
        fixture.withClasspath([pluginClasspathFile.parentFile]) {
            runner('helloWorld')
                .withPluginClasspath()
                .build()
        }

        then:
        Throwable t = thrown(InvalidPluginMetadataException)
        t.message == "Plugin metadata file '$PLUGIN_METADATA_FILE_NAME' does not contain expected property named '$IMPLEMENTATION_CLASSPATH_PROP_KEY'"
    }

    private void compilePluginProjectSources() {
        GFileUtils.mkdirs(projectDir)
        fixture.createPluginProjectSourceFiles(projectDir)
        new ForkingGradleExecuter(new UnderDevelopmentGradleDistribution(), testDirectoryProvider)
            .usingProjectDirectory(projectDir)
            .withArguments('classes', '--no-daemon')
            .run()
    }
}
