/*
 * Copyright 2015 the original author or authors.
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

import org.gradle.internal.nativeintegration.ProcessEnvironment
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.testfixtures.internal.NativeServicesTestFixture
import org.gradle.testkit.runner.fixtures.InjectsPluginClasspath
import org.gradle.testkit.runner.fixtures.InspectsBuildOutput
import org.gradle.testkit.runner.fixtures.InspectsExecutedTasks
import org.gradle.testkit.runner.fixtures.PluginUnderTest
import org.gradle.util.GradleVersion
import org.gradle.util.UsesNativeServices

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
import static org.hamcrest.CoreMatchers.anyOf
import static org.hamcrest.CoreMatchers.containsString

@InjectsPluginClasspath
@InspectsBuildOutput
@UsesNativeServices
@SuppressWarnings('IntegrationTestFixtures')
// result.output.contains does mean something different here
@Requires(value = IntegTestPreconditions.NotEmbeddedExecutor, reason = "Test causes builds to hang")
class GradleRunnerPluginClasspathInjectionIntegrationTest extends BaseGradleRunnerIntegrationTest {

    def plugin = new PluginUnderTest(1, file("plugin"))

    def "empty classpath is treated as no injected classpath"() {
        when:
        buildScript plugin.useDeclaration
        def result = runner()
            .withPluginClasspath([])
            .buildAndFail()

        then:
        execFailure(result).assertHasDescription("""
            |Plugin [id: '$plugin.id'] was not found in any of the following sources:
            |
            |- Gradle Core Plugins (plugin is not in 'org.gradle' namespace)
            |- $pluginRepositoriesDisplayName (plugin dependency must include a version number for this source)
        """.stripMargin().trim())
    }

    def "injected classpath is indicated in error message if plugin not found"() {
        when:
        buildScript plugin.useDeclaration
        def expectedClasspath = [file("blah1"), file("blah2")]
        def result = runner()
            .withPluginClasspath(expectedClasspath)
            .buildAndFail()

        then:
        execFailure(result).assertHasDescription("""
            |Plugin [id: '$plugin.id'] was not found in any of the following sources:
            |
            |- Gradle Core Plugins (plugin is not in 'org.gradle' namespace)
            |- Gradle TestKit (classpath: ${expectedClasspath*.absolutePath.join(File.pathSeparator)})
            |- $pluginRepositoriesDisplayName (plugin dependency must include a version number for this source)
        """.stripMargin().trim())
    }

    def "can inject plugin classpath and use in build"() {
        given:
        buildScript plugin.build().useDeclaration

        when:
        def result = runner(':helloWorld1')
            .withPluginClasspath(plugin.implClasspath)
            .build()

        then:
        result.task(":helloWorld1").outcome == SUCCESS
        result.output.contains('Hello world!1')
    }

    def "injected plugin classes are visible in build script applying plugin"() {
        given:
        buildScript plugin.build().useDeclaration + plugin.echoClassNameTask()

        when:
        def result = runner("echo1")
            .withPluginClasspath(plugin.implClasspath)
            .build()

        then:
        result.output.contains("class name: $plugin.taskClassName")
    }

    def "injected classes are not visible when plugin is not applied"() {
        given:
        buildScript plugin.echoClassNameTask()

        when:
        def result = runner('echo1', "-S")
            .withPluginClasspath(plugin.implClasspath)
            .buildAndFail()

        then:
        // This is how the class not being visible will manifest
        execFailure(result).assertThatCause(
            anyOf(
                containsString("Could not get unknown property 'org' for task ':echo1' of type org.gradle.api.DefaultTask."),
                containsString("Could not find property 'org' on task ':echo1'.")))
    }

    def "injected classes are inherited by child projects of project that applies plugin"() {
        given:
        file("settings.gradle") << "include 'child'"
        buildFile << plugin.build().useDeclaration
        file("child/build.gradle") << plugin.echoClassNameTask()

        when:
        def result = runner("child:echo1")
            .withPluginClasspath(plugin.implClasspath)
            .build()

        then:
        result.output.contains("class name: $plugin.taskClassName")
    }

    def "injected classes are not visible to projects at compile time that are not child projects of applying project"() {
        given:
        file("settings.gradle") << "include 'child'"
        buildFile << plugin.build().echoClassNameTask()
        file("child/build.gradle") << plugin.useDeclaration << plugin.echoClassNameTask()

        when:
        def result = runner(":child:echo1", "-S").withPluginClasspath(plugin.implClasspath).build()

        then:
        result.output.contains("class name: $plugin.taskClassName")

        when:
        result = runner("echo1", "-S").withPluginClasspath(plugin.implClasspath).buildAndFail()

        then:
        // This is how the class not being visible will manifest
        execFailure(result).assertThatCause(
            anyOf(
                containsString("Could not get unknown property 'org' for task ':echo1' of type org.gradle.api.DefaultTask."),
                containsString("Could not find property 'org' on task ':echo1'.")))
    }

    def "injected classes are not visible to projects at run time that are not child projects of applying project"() {
        given:
        file("settings.gradle") << "include 'child'"
        buildFile << plugin.build().echoClassNameTaskRuntime()
        file("child/build.gradle") << plugin.useDeclaration << plugin.echoClassNameTask()

        when:
        def result = runner(":child:echo1", "-S").withPluginClasspath(plugin.implClasspath).build()

        then:
        result.output.contains("class name: $plugin.taskClassName")

        when:
        result = runner(":echo1", "-S").withPluginClasspath(plugin.implClasspath).buildAndFail()

        then:
        execFailure(result).assertHasCause("failed to load class $plugin.taskClassName")
    }

    def "injected classes are loaded only once"() {
        when:
        file("settings.gradle") << "include 'child1', 'child2'"
        file("child1/build.gradle") << plugin.build().useDeclaration
        file("child2/build.gradle") << plugin.useDeclaration
        buildFile << """
            task compare {
                doLast {
                    project("child1").tasks.helloWorld1.getClass() == project("child2").tasks.helloWorld1.getClass()
                }
            }
        """

        then:
        runner("compare").withPluginClasspath(plugin.implClasspath).build()
    }

    @InspectsExecutedTasks
    def "plugin applied via injection can apply another plugin from its implementation classpath"() {
        given:
        plugin.file('src/main/groovy/org/gradle/test/CompositePlugin.groovy') << """
            package org.gradle.test

            import org.gradle.api.Plugin
            import org.gradle.api.Project

            class CompositePlugin implements Plugin<Project> {
                void apply(Project project) {
                    project.apply(plugin: $notation)
                }
            }
        """

        plugin.file('src/main/resources/META-INF/gradle-plugins/com.company.composite.properties') << """
            implementation-class=org.gradle.test.CompositePlugin
        """

        plugin.build()

        buildFile << """
            plugins {
                id "com.company.composite"
            }
        """

        when:
        def result = runner('helloWorld1')
            .withPluginClasspath(plugin.implClasspath)
            .build()

        then:
        result.task(":helloWorld1").outcome == SUCCESS

        where:
        type         | notation
        'class type' | 'HelloWorldPlugin1'
        'identifier' | "'com.company.helloworld1'"
    }

    @InspectsExecutedTasks
    def "injected classpath does not persist across builds"() {
        given:
        def counter = 0
        def otherPlugin = new PluginUnderTest(2, file("other")).build()
        plugin.build()

        when:
        buildFile << plugin.useDeclaration + (" " * counter++)
        def result = runner("helloWorld1")
            .withPluginClasspath(plugin.implClasspath)
            .build()

        then:
        result.task(":helloWorld1").outcome == SUCCESS

        when:
        buildFile.text = otherPlugin.useDeclaration + (" " * counter++)
        result = runner("helloWorld2")
            .withPluginClasspath(plugin.implClasspath)
            .buildAndFail()

        then:
        execFailure(result).assertHasDescription("Plugin [id: '$otherPlugin.id'] was not found in any of the following sources:")

        when:
        buildFile.text = otherPlugin.useDeclaration + (" " * counter++)
        result = runner("helloWorld2")
            .withPluginClasspath(otherPlugin.implClasspath)
            .build()

        then:
        result.task(":helloWorld2").outcome == SUCCESS

        when:
        buildFile.text = plugin.useDeclaration + (" " * counter)
        result = runner("helloWorld1")
            .withPluginClasspath(otherPlugin.implClasspath)
            .buildAndFail()

        then:
        execFailure(result).assertHasDescription("Plugin [id: '$plugin.id'] was not found in any of the following sources:")
    }

    @Requires(value = IntegTestPreconditions.NotEmbeddedExecutor, reason = "classloader isolation does not work here in embedded mode")
    @InspectsExecutedTasks
    def "buildSrc classes are not visible to injected classes"() {
        plugin.build()
        def buildSrcSrcDir = file("buildSrc/src/main/groovy/org/gradle/test")

        // these class names intentionally clash with what we are injecting

        buildSrcSrcDir.file("HelloWorldPlugin1.groovy") << """
            package org.gradle.test

            import org.gradle.api.Plugin
            import org.gradle.api.Project

            class HelloWorldPlugin1 implements Plugin<Project> {
                void apply(Project project) {
                    project.task('helloWorldBuildSrc', type: HelloWorld1)
                }
            }
        """

        buildSrcSrcDir.file("HelloWorld1.groovy") << """
            package org.gradle.test

            import org.gradle.api.DefaultTask
            import org.gradle.api.tasks.TaskAction

            class HelloWorld1 extends DefaultTask {
                @TaskAction
                void doSomething() {
                    println 'Hello world! (buildSrc)'
                }
            }
        """

        buildFile << plugin.useDeclaration << """
            assert tasks.helloWorld1.getClass().classLoader != org.gradle.test.HelloWorldPlugin1.classLoader
            apply plugin: org.gradle.test.HelloWorldPlugin1 // should be from buildSrc
        """

        when:
        def result = runner("helloWorld1", "helloWorldBuildSrc")
            .withPluginClasspath(plugin.implClasspath)
            .build()

        then:
        result.task(":helloWorld1").outcome == SUCCESS
        result.task(":helloWorldBuildSrc").outcome == SUCCESS
        result.output.contains "Hello world!1"
        result.output.contains "Hello world! (buildSrc)"
    }

    static class FileSubclass extends File {
        FileSubclass(File var1) {
            super(var1.absolutePath)
        }
    }

    @InspectsExecutedTasks
    def "injected classpath may contain File subclasses"() {
        given:
        buildFile << plugin.build().useDeclaration

        when:
        def result = runner(':helloWorld1')
            .withPluginClasspath(plugin.implClasspath.collect { new FileSubclass(it) })
            .build()

        then:
        result.task(":helloWorld1").outcome == SUCCESS
    }

    def "can use relative files as part of injected classpath"() {
        given:
        file("changed/settings.gradle").createFile()
        file("changed/build.gradle") << plugin.build().useDeclaration
        def relClasspath = plugin.implClasspath.collect {
            def path = new File("").toURI().relativize(it.toURI()).getPath()
            new File(path)
        }

        def runner = runner('helloWorld1')
            .withProjectDir(file("changed"))
            .withPluginClasspath(relClasspath)


        def processEnvironment = NativeServicesTestFixture.instance.get(ProcessEnvironment)
        when:
        def orig = new File("").getAbsoluteFile()
        def result = null
        try {
            if (!processEnvironment.maybeSetProcessDir(file("changed"))) {
                return
            }
            result = runner.build()
        } finally {
            processEnvironment.maybeSetProcessDir(orig)
        }

        then:
        result.task(":helloWorld1").outcome == SUCCESS
        result.output.contains('Hello world!1')
    }

    private static String getPluginRepositoriesDisplayName() {
        return gradleVersion >= GradleVersion.version("4.4")
            ? "Plugin Repositories"
            : "Gradle Central Plugin Repository"
    }
}
