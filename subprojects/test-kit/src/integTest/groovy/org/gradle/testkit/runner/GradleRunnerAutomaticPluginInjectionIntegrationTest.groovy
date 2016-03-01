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

    def "automatically injects plugin classpath if manifest is found"() {
        given:
        List<File> pluginClasspath = fixture.getPluginClasspath(projectDir)
        File pluginClasspathFile = fixture.createPluginClasspathManifestFile(projectDir, pluginClasspath)

        when:
        BuildResult result = fixture.withClasspath([pluginClasspathFile]) {
            runner('helloWorld').build()
        }

        then:
        result.task(":helloWorld").outcome == SUCCESS
    }

    def "automatically injected plugin classpath can be overridden"() {
        given:
        List<File> pluginClasspath = fixture.getPluginClasspath(projectDir)
        File pluginClasspathFile = fixture.createPluginClasspathManifestFile(projectDir, pluginClasspath)
        List<File> userDefinedClasspath = [projectDir.file('does/not/exist')]

        when:
        BuildResult result = fixture.withClasspath([pluginClasspathFile]) {
            runner('helloWorld').withPluginClasspath(userDefinedClasspath).buildAndFail()
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

    def "does not inject plugin classpath if manifest is not found"() {
        when:
        BuildResult result = runner('helloWorld').buildAndFail()

        then:
        execFailure(result).assertHasDescription("""
            |Plugin [id: 'com.company.helloworld'] was not found in any of the following sources:
            |
            |- Gradle Core Plugins (plugin is not in 'org.gradle' namespace)
            |- Gradle Central Plugin Repository (plugin dependency must include a version number for this source)
        """.stripMargin().trim())
    }

    def "does not inject plugin classpath if manifest content is empty"() {
        given:
        File pluginClasspathFile = fixture.createPluginClasspathManifestFile(projectDir, [])

        when:
        BuildResult result = fixture.withClasspath([pluginClasspathFile]) {
            runner('helloWorld').buildAndFail()
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

    def "throws exception if manifest does not contain classpath property"() {
        given:
        File pluginClasspathFile = fixture.createPluginClasspathManifestFile(projectDir, [], ['other': 'prop'])

        when:
        fixture.withClasspath([pluginClasspathFile]) {
            runner('helloWorld').build()
        }

        then:
        Throwable t = thrown(IncompletePluginMetadataException)
        t.message == "Plugin classpath manifest file does not contain expected property named 'implementation-classpath'"
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
