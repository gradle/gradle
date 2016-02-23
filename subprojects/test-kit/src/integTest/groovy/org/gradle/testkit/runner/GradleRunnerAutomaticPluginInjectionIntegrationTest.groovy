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
import org.gradle.integtests.fixtures.versions.ReleasedVersionDistributions
import org.gradle.testkit.runner.fixtures.annotations.InjectsPluginClasspath
import org.gradle.testkit.runner.fixtures.annotations.InspectsBuildOutput
import org.gradle.testkit.runner.fixtures.annotations.NoDebug
import org.gradle.testkit.runner.internal.feature.TestKitFeature
import org.gradle.util.GFileUtils
import org.gradle.util.UsesNativeServices

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

@InjectsPluginClasspath
@InspectsBuildOutput
@UsesNativeServices
class GradleRunnerAutomaticPluginInjectionIntegrationTest extends GradleRunnerIntegrationTest {

    private static final ReleasedVersionDistributions RELEASED_VERSION_DISTRIBUTIONS = new ReleasedVersionDistributions()
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
        File pluginClasspathFile = createPluginClasspathManifestFile(getPluginClasspath())

        when:
        BuildResult result = withClasspath([pluginClasspathFile]) {
            runner('helloWorld').build()
        }

        then:
        result.task(":helloWorld").outcome == SUCCESS
    }

    def "automatically injected plugin classpath can be overridden"() {
        given:
        File pluginClasspathFile = createPluginClasspathManifestFile(getPluginClasspath())
        List<File> userDefinedClasspath = [projectDir.file('does/not/exist')]

        when:
        BuildResult result = withClasspath([pluginClasspathFile]) {
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

    @NoDebug
    def "plugin classpath is not injected automatically if target Gradle version does not support feature"() {
        given:
        File pluginClasspathFile = createPluginClasspathManifestFile(getPluginClasspath())
        String unsupportedGradleVersion = RELEASED_VERSION_DISTRIBUTIONS.getPrevious(TestKitFeature.PLUGIN_CLASSPATH_INJECTION.since).version.version

        when:
        BuildResult result = withClasspath([pluginClasspathFile]) {
            runner('helloWorld').withGradleVersion(unsupportedGradleVersion).buildAndFail()
        }

        then:
        execFailure(result).assertHasDescription("""
            |Plugin [id: 'com.company.helloworld'] was not found in any of the following sources:
            |
            |- Gradle Core Plugins (plugin is not in 'org.gradle' namespace)
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
        File pluginClasspathFile = createPluginClasspathManifestFile([])

        when:
        BuildResult result = withClasspath([pluginClasspathFile]) {
            runner('helloWorld').buildAndFail()
        }

        then:
        pluginClasspathFile.exists()
        pluginClasspathFile.text == ''
        execFailure(result).assertHasDescription("""
            |Plugin [id: 'com.company.helloworld'] was not found in any of the following sources:
            |
            |- Gradle Core Plugins (plugin is not in 'org.gradle' namespace)
            |- Gradle Central Plugin Repository (plugin dependency must include a version number for this source)
        """.stripMargin().trim())
    }

    private void compilePluginProjectSources() {
        GFileUtils.mkdirs(projectDir)
        createPluginProjectSourceFiles()
        new ForkingGradleExecuter(new UnderDevelopmentGradleDistribution(), testDirectoryProvider)
            .usingProjectDirectory(projectDir)
            .withArguments('classes', '--no-daemon')
            .run()
    }

    private void createPluginProjectSourceFiles() {
        projectDir.file("src/main/groovy/org/gradle/test/HelloWorldPlugin.groovy") << """
            package org.gradle.test

            import org.gradle.api.Plugin
            import org.gradle.api.Project

            class HelloWorldPlugin implements Plugin<Project> {
                void apply(Project project) {
                    project.task('helloWorld', type: HelloWorld)
                }
            }
        """

        projectDir.file("src/main/groovy/org/gradle/test/HelloWorld.groovy") << """
            package org.gradle.test

            import org.gradle.api.DefaultTask
            import org.gradle.api.tasks.TaskAction

            class HelloWorld extends DefaultTask {
                @TaskAction
                void doSomething() {
                    println 'Hello world!'
                }
            }
        """

        projectDir.file("src/main/resources/META-INF/gradle-plugins/com.company.helloworld.properties") << """
            implementation-class=org.gradle.test.HelloWorldPlugin
        """

        projectDir.file("build.gradle") << """
            apply plugin: 'groovy'

            dependencies {
                compile gradleApi()
                compile localGroovy()
            }
        """
    }

    private List<File> getPluginClasspath() {
        [projectDir.file("build/classes/main"), projectDir.file('build/resources/main')]
    }

    private File createPluginClasspathManifestFile(List<File> classpath) {
        String content = classpath.collect { it.absolutePath.replaceAll('\\\\', '/') }.join('\n')
        File pluginClasspathFile = projectDir.file("build/generatePluginClasspathManifest/plugin-classpath.txt")
        pluginClasspathFile << content
        pluginClasspathFile
    }

    def withClasspath(List<File> classpathFiles, Closure closure) {
        ClassLoader originalClassLoader = getClass().classLoader

        try {
            URLClassLoader classLoader = new URLClassLoader(classpathFiles.collect { file -> file.toURI().toURL() } as URL[], Thread.currentThread().contextClassLoader)
            Thread.currentThread().contextClassLoader = classLoader
            return closure()
        } finally {
            Thread.currentThread().contextClassLoader = originalClassLoader
        }
    }
}
