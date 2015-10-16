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

import org.gradle.integtests.fixtures.executer.ForkingGradleExecuter
import org.gradle.integtests.fixtures.executer.UnderDevelopmentGradleDistribution
import org.gradle.internal.nativeintegration.ProcessEnvironment
import org.gradle.test.fixtures.file.TestFile
import org.gradle.testfixtures.internal.NativeServicesTestFixture
import org.gradle.util.UsesNativeServices
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

@UsesNativeServices
class GradleRunnerPluginInjectionIntegrationTest extends AbstractGradleRunnerIntegrationTest {

    def "unresolvable plugin for provided empty classpath fails build and indicates searched locations"() {
        when:
        buildFile << pluginDeclaration()
        def result = runner()
            .withPluginClasspath([])
            .buildAndFail()

        then:
        execFailure(result).assertHasDescription("""
            |Plugin [id: 'com.company.helloworld1'] was not found in any of the following sources:
            |
            |- Gradle Core Plugins (plugin is not in 'org.gradle' namespace)
            |- Gradle Central Plugin Repository (plugin dependency must include a version number for this source)
        """.stripMargin().trim())
    }

    def "unresolvable plugin for provided classpath fails build and indicates searched classpath"() {
        when:
        buildFile << pluginDeclaration()
        def expectedClasspath = [file("blah1"), file("blah2")]
        def result = runner()
            .withPluginClasspath(expectedClasspath)
            .buildAndFail()

        then:
        execFailure(result).assertHasDescription("""
            |Plugin [id: 'com.company.helloworld1'] was not found in any of the following sources:
            |
            |- Gradle Core Plugins (plugin is not in 'org.gradle' namespace)
            |- Gradle TestKit (classpath: ${expectedClasspath*.absolutePath.join(File.pathSeparator)})
            |- Gradle Central Plugin Repository (plugin dependency must include a version number for this source)
        """.stripMargin().trim())
    }

    def "can resolve plugin for provided classpath"() {
        given:
        compilePluginProjectSources()
        buildFile << pluginDeclaration()

        when:
        def result = runner('helloWorld1')
            .withPluginClasspath(getPluginClasspath())
            .build()

        then:
        result.task(":helloWorld1").outcome == SUCCESS
        result.standardOutput.contains('Hello world! (1)')
    }

    def "injected classes are visible in root build script when applied to root"() {
        given:
        compilePluginProjectSources()
        buildFile << pluginDeclaration() << echoClassNameTask()

        when:
        def result = runner("echo1")
            .withPluginClasspath(getPluginClasspath())
            .build()

        then:
        result.standardOutput.contains("class name: org.gradle.test.HelloWorld1")
    }

    def "injected classes are not visible in root script when plugin is not applied"() {
        given:
        compilePluginProjectSources()
        buildFile << echoClassNameTask()

        when:
        def result = runner('echo1')
            .withPluginClasspath(getPluginClasspath())
            .buildAndFail()

        then:
        // This is how the class not being visible will manifest
        execFailure(result).assertHasCause("Could not find property 'org' on task ':echo1'.")
    }

    def "injected classes are visible in child build script when applied to root"() {
        given:
        compilePluginProjectSources()
        file("settings.gradle") << "include 'child'"
        buildFile << pluginDeclaration()
        file("child/build.gradle") << echoClassNameTask()

        when:
        def result = runner("child:echo1")
            .withPluginClasspath(getPluginClasspath())
            .build()

        then:
        result.standardOutput.contains("class name: org.gradle.test.HelloWorld1")
    }

    def "injected classes are not visible in root build script at compile time when applied to child"() {
        given:
        compilePluginProjectSources()
        file("settings.gradle") << "include 'child'"
        buildFile << echoClassNameTask()
        file("child/build.gradle") << pluginDeclaration() << echoClassNameTask()

        when:
        def result = runner("child:echo1").withPluginClasspath(getPluginClasspath()).build()

        then:
        result.standardOutput.contains("class name: org.gradle.test.HelloWorld1")

        when:
        result = runner("echo1").withPluginClasspath(getPluginClasspath()).buildAndFail()

        then:
        // This is how the class not being visible will manifest
        execFailure(result).assertHasCause("Could not find property 'org' on task ':echo1'.")
    }

    def "injected classes are not visible in root build script at run time when applied to child"() {
        given:
        compilePluginProjectSources()
        file("settings.gradle") << "include 'child'"
        buildFile << echoClassNameTaskRuntime()
        file("child/build.gradle") << pluginDeclaration() << echoClassNameTask()

        when:
        def result = runner("child:echo1").withPluginClasspath(getPluginClasspath()).build()

        then:
        result.standardOutput.contains("class name: org.gradle.test.HelloWorld1")

        when:
        result = runner("echo1").withPluginClasspath(getPluginClasspath()).buildAndFail()

        then:
        execFailure(result).assertHasCause("failed to load class org.gradle.test.HelloWorld1")
    }

    def "injected classes are loaded only once"() {
        given:
        compilePluginProjectSources()
        file("settings.gradle") << "include 'child1', 'child2'"
        file("child1/build.gradle") << pluginDeclaration()
        file("child2/build.gradle") << pluginDeclaration()
        buildFile << """
            task compare << {
              project("child1").tasks.helloWorld1.getClass() == project("child2").tasks.helloWorld1.getClass()
            }
        """

        when:
        def result = runner("compare").withPluginClasspath(getPluginClasspath()).build()

        then:
        result.task(":compare").outcome == SUCCESS
    }

    @Unroll
    def "can resolve plugin for provided classpath that applies another plugin under test by #type"() {
        given:
        pluginProjectFile('src/main/groovy/org/gradle/test/CompositePlugin.groovy') << """
            package org.gradle.test

            import org.gradle.api.Plugin
            import org.gradle.api.Project

            class CompositePlugin implements Plugin<Project> {
                void apply(Project project) {
                    project.apply(plugin: $notation)
                }
            }
        """

        pluginProjectFile('src/main/resources/META-INF/gradle-plugins/com.company.composite.properties') << """
            implementation-class=org.gradle.test.CompositePlugin
        """

        compilePluginProjectSources()
        buildFile << """
            plugins {
                id "com.company.composite"
            }
        """

        when:
        def result = runner('helloWorld1')
            .withPluginClasspath(getPluginClasspath())
            .build()

        then:
        result.task(":helloWorld1").outcome == SUCCESS

        where:
        type         | notation
        'class type' | 'HelloWorldPlugin1'
        'identifier' | "'com.company.helloworld1'"
    }

    def "can apply single plugin from injected classpath containing multiple"() {
        given:
        compilePluginProjectSources(1)
        compilePluginProjectSources(2)
        buildFile << pluginDeclaration(2) << echoClassNameTask(1)

        when:
        def result = runner("echo1")
            .withPluginClasspath(getPluginClasspath(1) + getPluginClasspath(2))
            .build()

        then:
        // plugin 1 class is visible, as the classpath is loaded in one loader
        result.standardOutput.contains("class name: org.gradle.test.HelloWorld1")

        when:
        result = runner("helloWorld1")
            .withPluginClasspath(getPluginClasspath(1) + getPluginClasspath(2))
            .buildAndFail()

        then:
        // only plugin 2 was actually applied though
        execFailure(result).assertHasDescription("Task 'helloWorld1' not found in root project")

        when:
        result = runner("helloWorld2")
            .withPluginClasspath(getPluginClasspath(1) + getPluginClasspath(2))
            .build()

        then:
        result.task(":helloWorld2").outcome == SUCCESS
    }

    def "injected class path can change between invocations"() {
        given:
        compilePluginProjectSources(1)
        compilePluginProjectSources(2)

        when:
        buildFile << pluginDeclaration(1)
        def result = runner("helloWorld1")
            .withPluginClasspath(getPluginClasspath(1))
            .build()

        then:
        result.task(":helloWorld1").outcome == SUCCESS

        when:
        buildFile.text = pluginDeclaration(2)
        result = runner("helloWorld2")
            .withPluginClasspath(getPluginClasspath(1))
            .buildAndFail()

        then:
        execFailure(result).assertHasDescription("Plugin [id: 'com.company.helloworld2'] was not found in any of the following sources:")

        when:
        buildFile.text = pluginDeclaration(2)
        result = runner("helloWorld2")
            .withPluginClasspath(getPluginClasspath(2))
            .build()

        then:
        result.task(":helloWorld2").outcome == SUCCESS

        when:
        buildFile.text = pluginDeclaration(1)
        result = runner("helloWorld1")
            .withPluginClasspath(getPluginClasspath(2))
            .buildAndFail()

        then:
        execFailure(result).assertHasDescription("Plugin [id: 'com.company.helloworld1'] was not found in any of the following sources:")
    }

    def "injected classes are not affected by buildSrc"() {
        compilePluginProjectSources()
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

        buildFile << pluginDeclaration() << """
            assert tasks.helloWorld1.getClass().classLoader != org.gradle.test.HelloWorldPlugin1.classLoader
            apply plugin: org.gradle.test.HelloWorldPlugin1 // should be from buildSrc
        """

        when:
        def result = runner("helloWorld1", "helloWorldBuildSrc")
            .withPluginClasspath(getPluginClasspath())
            .build()

        then:
        result.task(":helloWorld1").outcome == SUCCESS
        result.task(":helloWorldBuildSrc").outcome == SUCCESS
        result.standardOutput.contains "Hello world! (1)"
        result.standardOutput.contains "Hello world! (buildSrc)"
    }

    static class FileSubclass extends File {
        FileSubclass(File var1) {
            super(var1.absolutePath)
        }
    }

    def "can use File subclass as part of classpath"() {
        given:
        compilePluginProjectSources()
        buildFile << pluginDeclaration()

        when:
        def result = runner('helloWorld1')
            .withPluginClasspath(getPluginClasspath().collect { new FileSubclass(it) })
            .build()

        then:
        result.task(":helloWorld1").outcome == SUCCESS
        result.standardOutput.contains('Hello world! (1)')
    }


    def "can use relative files as part of classpath"() {
        given:
        compilePluginProjectSources()
        file("changed/build.gradle") << pluginDeclaration()
        def relClasspath = getPluginClasspath().collect {
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
        result.standardOutput.contains('Hello world! (1)')
    }

    static String echoClassNameTask(int counter = 1) {
        """
            task echo$counter << {
                println "class name: " + org.gradle.test.HelloWorld${counter}.name
            }
        """
    }

    static String echoClassNameTaskRuntime(int counter = 1) {
        """
            def loader = getClass().classLoader
            task echo$counter << {
                try {
                  println "class name: " + loader.loadClass("org.gradle.test.HelloWorld${counter}").name
                } catch (ClassNotFoundException e) {
                  throw new RuntimeException("failed to load class org.gradle.test.HelloWorld${counter}")
                }
            }
        """
    }

    static String pluginDeclaration(int counter = 1) {
        """
        plugins {
            id 'com.company.helloworld$counter'
        }
        """
    }

    private void compilePluginProjectSources(int counter = 1) {
        createPluginProjectSourceFiles(counter)
        new ForkingGradleExecuter(new UnderDevelopmentGradleDistribution(), testProjectDir)
            .usingProjectDirectory(file(counter.toString()))
            .withGradleUserHomeDir(testKitWorkspace)
            .withDaemonBaseDir(testKitWorkspace.file('daemon'))
            .withArguments('classes')
            .run()
    }

    private void createPluginProjectSourceFiles(int counter = 1) {
        pluginProjectFile(counter, "src/main/groovy/org/gradle/test/HelloWorldPlugin${counter}.groovy") << """
            package org.gradle.test

            import org.gradle.api.Plugin
            import org.gradle.api.Project

            class HelloWorldPlugin${counter} implements Plugin<Project> {
                void apply(Project project) {
                    project.task('helloWorld${counter}', type: HelloWorld${counter})
                }
            }
        """

        pluginProjectFile(counter, "src/main/groovy/org/gradle/test/HelloWorld${counter}.groovy") << """
            package org.gradle.test

            import org.gradle.api.DefaultTask
            import org.gradle.api.tasks.TaskAction

            class HelloWorld${counter} extends DefaultTask {
                @TaskAction
                void doSomething() {
                    println 'Hello world! (${counter})'
                }
            }
        """

        pluginProjectFile(counter, "src/main/resources/META-INF/gradle-plugins/com.company.helloworld${counter}.properties") << """
            implementation-class=org.gradle.test.HelloWorldPlugin${counter}
        """

        pluginProjectFile(counter, 'build.gradle') << """
            apply plugin: 'groovy'

            dependencies {
                compile gradleApi()
                compile localGroovy()
            }
        """
    }

    private TestFile pluginProjectFile(int counter = 1, String path) {
        testProjectDir.file(counter.toString()).file(path)
    }

    private List<File> getPluginClasspath(int counter = 1) {
        [pluginProjectFile(counter, "build/classes/main"), pluginProjectFile(counter, 'build/resources/main')]
    }
}
