/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.build.event

import org.gradle.api.services.BuildServiceParameters
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.test.fixtures.file.TestFile
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.OperationCompletionListener
import org.gradle.tooling.events.task.TaskFailureResult
import org.gradle.tooling.events.task.TaskFinishEvent
import org.gradle.tooling.events.task.TaskSkippedResult
import org.gradle.tooling.events.task.TaskSuccessResult
import org.gradle.util.internal.TextUtil
import spock.lang.IgnoreIf
import spock.lang.Issue

class BuildEventsIntegrationTest extends AbstractIntegrationSpec {
    def "listener can subscribe to task completion events"() {
        loggingListener()
        registeringPlugin()
        buildFile << """
            apply plugin: LoggingPlugin

            task notUpToDate {
                doFirst { println("not up-to-date") }
            }
            task upToDate {
                def file = file("thing.txt")
                outputs.file(file)
                doFirst { file.text = "thing" }
            }
            task broken {
                dependsOn notUpToDate, upToDate
                doFirst {
                    throw new RuntimeException()
                }
            }
        """

        when:
        run("notUpToDate")

        then:
        output.count("EVENT:") == 1
        outputContains("EVENT: finish :notUpToDate OK")

        when:
        run("notUpToDate")

        then:
        output.count("EVENT:") == 1
        outputContains("EVENT: finish :notUpToDate OK")

        when:
        fails("broken")

        then:
        output.count("EVENT:") == 3
        outputContains("EVENT: finish :notUpToDate OK")
        outputContains("EVENT: finish :upToDate OK")
        outputContains("EVENT: finish :broken FAILED")

        when:
        fails("broken")

        then:
        output.count("EVENT:") == 3
        outputContains("EVENT: finish :notUpToDate OK")
        outputContains("EVENT: finish :upToDate UP-TO-DATE")
        outputContains("EVENT: finish :broken FAILED")
    }

    def "plugin applied to multiple projects can register a shared listener"() {
        settingsFile << """
            include 'a', 'b'
        """
        loggingListener()
        registeringPlugin()
        buildFile << """
            subprojects {
                apply plugin: LoggingPlugin
                task thing { }
            }
        """

        when:
        run("a:thing")

        then:
        output.count("EVENT:") == 1
        outputContains("EVENT: finish :a:thing")

        when:
        run("a:thing")

        then:
        output.count("EVENT:") == 1
        outputContains("EVENT: finish :a:thing")

        when:
        run("thing")

        then:
        output.count("EVENT:") == 2
        outputContains("EVENT: finish :a:thing")
        outputContains("EVENT: finish :b:thing")

        when:
        run("thing")

        then:
        output.count("EVENT:") == 2
        outputContains("EVENT: finish :a:thing")
        outputContains("EVENT: finish :b:thing")
    }

    def "listener receives task completion events from included builds"() {
        settingsFile << """
            includeBuild 'a'
            includeBuild 'b'
        """
        loggingListener()
        registeringPlugin()
        buildFile << """
            apply plugin: LoggingPlugin
            task thing {
                dependsOn gradle.includedBuilds*.task(':thing')
            }
        """
        file("a/build.gradle") << """
            task thing
        """
        file("b/build.gradle") << """
            task thing
        """

        when:
        run("thing")

        then:
        output.count("EVENT:") == 3
        outputContains("EVENT: finish :thing")
        outputContains("EVENT: finish :a:thing")
        outputContains("EVENT: finish :b:thing")

        when:
        run("thing")

        then:
        output.count("EVENT:") == 3
        outputContains("EVENT: finish :thing")
        outputContains("EVENT: finish :a:thing")
        outputContains("EVENT: finish :b:thing")
    }

    @IgnoreIf({ GradleContextualExecuter.configCache }) // already covers CC
    def "listener is not discarded after configuration phase when used with configuration cache"() {
        listenerReceivedConfigurationTimeData()
        registeringPlugin()
        buildFile << """
            apply plugin: LoggingPlugin

            def listener = gradle.sharedServices.registrations["listener"].service.get()
            listener.configTime("data")

            task thing {
                doLast { }
            }
        """
        executer.beforeExecute {
            withArgument("--configuration-cache")
            withArgument("-Dorg.gradle.configuration-cache.internal.load-after-store=true")
        }

        when:
        run("thing")

        then:
        output.count("service:") == 2
        outputContains("service: finish :thing with data=data")
        outputContains("service: closed with data=data")

        when:
        run("thing")

        then:
        output.count("service:") == 2
        outputContains("service: finish :thing with data=null")
        outputContains("service: closed with data=null")
    }

    def "listener registered from init script can receive task completion events from buildSrc and main build"() {
        def initScript = file("init.gradle")
        loggingListener(initScript)
        initScript << """
            if (gradle.parent == null) {
                def listener = gradle.sharedServices.registerIfAbsent("listener", LoggingListener) { }
                services.get(${BuildEventsListenerRegistry.name}).onTaskCompletion(listener)
            }
        """
        file("buildSrc/src/main/java/Thing.java") << """
            class Thing { }
        """
        buildFile << """
            task thing {
            }
        """

        when:
        executer.usingInitScript(initScript)
        run("thing")

        then:
        output.count("EVENT:") == 6
        outputContains("EVENT: finish :buildSrc:processResources SKIPPED")
        outputContains("EVENT: finish :buildSrc:compileJava OK")
        outputContains("EVENT: finish :thing UP-TO-DATE")

        when:
        executer.usingInitScript(initScript)
        run("thing")

        then:
        outputContains("EVENT: finish :thing UP-TO-DATE")
    }

    def "reports failure to handle event and continues with task execution"() {
        loggingListener()
        brokenListener()
        buildFile << """
            import ${BuildEventsListenerRegistry.name}

            def registry = project.services.get(BuildEventsListenerRegistry)
            registry.onTaskCompletion(gradle.sharedServices.registerIfAbsent('broken', BrokenListener) { })
            registry.onTaskCompletion(gradle.sharedServices.registerIfAbsent('listener', LoggingListener) { })

            task a
            task b { dependsOn a }
        """

        when:
        fails("b")

        then:
        // TODO - add some context to the failure
        failure.assertHasDescription("broken")

        output.count("BROKEN:") == 1

        output.count("EVENT:") == 2
        outputContains("EVENT: finish :a")
        outputContains("EVENT: finish :b")

        when:
        fails("b")

        then:
        // TODO - add some context to the failure
        failure.assertHasDescription("broken")

        output.count("BROKEN:") == 1

        output.count("EVENT:") == 2
        outputContains("EVENT: finish :a")
        outputContains("EVENT: finish :b")
    }

    def "reports failure to create listener and continues with task execution"() {
        loggingListener()
        cannotConstructListener()
        buildFile << """
            import ${BuildEventsListenerRegistry.name}

            def registry = project.services.get(BuildEventsListenerRegistry)
            registry.onTaskCompletion(gradle.sharedServices.registerIfAbsent('broken', BrokenListener) { })
            registry.onTaskCompletion(gradle.sharedServices.registerIfAbsent('listener', LoggingListener) { })

            task a
            task b { dependsOn a }
        """

        when:
        fails("b")

        then:
        // TODO - add some context to the failure
        failure.assertHasDescription("Failed to create service 'broken'.")
        failure.assertHasCause("broken")

        output.count("BROKEN:") == 1

        output.count("EVENT:") == 2
        outputContains("EVENT: finish :a")
        outputContains("EVENT: finish :b")

        when:
        fails("b")

        then:
        // TODO - add some context to the failure
        failure.assertHasDescription("Failed to create service 'broken'.")
        failure.assertHasCause("broken")

        output.count("BROKEN:") == 1

        output.count("EVENT:") == 2
        outputContains("EVENT: finish :a")
        outputContains("EVENT: finish :b")
    }

    @IgnoreIf({ GradleContextualExecuter.embedded })
    // Tries to resolve external (api) jars that are not available in the embedded environment
    @Issue("https://github.com/gradle/gradle/issues/16774")
    def "can use plugin that registers build event listener with ProjectBuilder"() {
        given:
        file("build.gradle") << """
            plugins { id 'groovy-gradle-plugin' }
            repositories { mavenCentral() }
            dependencies { testImplementation("junit:junit:4.13") }
            test.testLogging {
                showStandardStreams = true
                showExceptions = true
            }
        """
        def plugin = file('src/main/groovy/my-plugin.gradle')
        loggingListener(plugin)
        plugin << """
            def listener = project.gradle.sharedServices.registerIfAbsent("listener", LoggingListener) { }
            gradle.services.get(${BuildEventsListenerRegistry.name}).onTaskCompletion(listener)
        """
        file("src/test/groovy/my/MyTest.groovy") << """
            package my
            import org.gradle.testfixtures.*
            import org.junit.Test
            class MyTest {
                @Test void test() {
                    def project = ProjectBuilder.builder().build()
                    project.plugins.apply("my-plugin")
                }
            }
        """

        when:
        run 'test'

        then:
        executedAndNotSkipped(':test')

        // ensure the test has been executed
        def result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('my.MyTest')
        result.testClass('my.MyTest').assertTestCount(1, 0, 0)
    }

    @IgnoreIf({ GradleContextualExecuter.embedded })
    // Cannot run TestKit in embedded mode
    def "can use plugin that registers build event listener with TestKit"() {
        given:
        def plugin = file('src/main/groovy/my-plugin.gradle')
        loggingListener(plugin)
        plugin << """
            def listener = project.gradle.sharedServices.registerIfAbsent("listener", LoggingListener) { }
            gradle.services.get(${BuildEventsListenerRegistry.name}).onTaskCompletion(listener)
            println('listener registered')
        """

        file("build.gradle") << """
            plugins { id 'groovy-gradle-plugin' }
            repositories { mavenCentral() }
            dependencies { testImplementation("junit:junit:4.13") }
            test.testLogging {
                showStandardStreams = true
                showExceptions = true
            }
        """

        def testProjectDir = file("testTmp").tap { it.mkdirs() }

        file("src/test/groovy/my/MyTest.groovy") << """
            package my
            import org.gradle.testfixtures.*
            import org.gradle.testkit.runner.GradleRunner
            import org.junit.Test

            class MyTest {
                @Test void test() {
                    def projectDir = new File("${TextUtil.normaliseFileSeparators(testProjectDir.absolutePath)}")
                    new File(projectDir, 'settings.gradle').text = ""
                    new File(projectDir, 'build.gradle').text = '''
                        plugins { id 'my-plugin' }
                    '''

                    def runner = GradleRunner.create()
                    runner.forwardOutput()
                    runner.withPluginClasspath()
                    runner.withArguments("help")
                    runner.withProjectDir(projectDir)
                    runner.withDebug(true)
                    def result = runner.build()
                }
            }
        """

        when:
        run 'test'

        then:
        executedAndNotSkipped(':test')
        outputContains("listener registered")

        // ensure the test has been executed
        def result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('my.MyTest')
        result.testClass('my.MyTest').assertTestCount(1, 0, 0)
    }

    void loggingListener(TestFile file = buildFile) {
        file << """
            import ${OperationCompletionListener.name}
            import ${FinishEvent.name}
            import ${TaskFinishEvent.name}
            import ${TaskSuccessResult.name}
            import ${TaskFailureResult.name}
            import ${TaskSkippedResult.name}
            import ${BuildServiceParameters.name}

            abstract class LoggingListener implements OperationCompletionListener, BuildService<BuildServiceParameters.None> {
                @Override
                void onFinish(FinishEvent event) {
                    if (event instanceof TaskFinishEvent) {
                        print("EVENT: finish \${event.descriptor.taskPath}")
                        if (event.result instanceof TaskSuccessResult) {
                            if (event.result.upToDate) {
                                println(" UP-TO-DATE")
                            } else {
                                println(" OK")
                            }
                        } else if (event.result instanceof TaskFailureResult) {
                            println(" FAILED")
                        } else if (event.result instanceof TaskSkippedResult) {
                            println(" SKIPPED")
                        } else {
                            throw new IllegalArgumentException()
                        }
                    } else {
                        throw new IllegalArgumentException()
                    }
                }
            }
        """
    }

    def cannotConstructListener() {
        buildFile << """
            import ${OperationCompletionListener.name}
            import ${FinishEvent.name}
            import ${TaskFinishEvent.name}
            import ${TaskSuccessResult.name}
            import ${TaskFailureResult.name}
            import ${TaskSkippedResult.name}
            import ${BuildServiceParameters.name}

            abstract class BrokenListener implements OperationCompletionListener, BuildService<BuildServiceParameters.None> {
                BrokenListener() {
                    println("BROKEN: created")
                    throw new RuntimeException("broken")
                }
                @Override
                void onFinish(FinishEvent event) {
                    println("BROKEN: received event")
                }
            }
        """
    }

    def brokenListener() {
        buildFile << """
            import ${OperationCompletionListener.name}
            import ${FinishEvent.name}
            import ${TaskFinishEvent.name}
            import ${TaskSuccessResult.name}
            import ${TaskFailureResult.name}
            import ${TaskSkippedResult.name}
            import ${BuildServiceParameters.name}

            abstract class BrokenListener implements OperationCompletionListener, BuildService<BuildServiceParameters.None> {
                @Override
                void onFinish(FinishEvent event) {
                    println("BROKEN: received event")
                    throw new RuntimeException("broken")
                }
            }
        """
    }

    void listenerReceivedConfigurationTimeData(TestFile file = buildFile) {
        file << """
            import ${OperationCompletionListener.name}
            import ${FinishEvent.name}
            import ${TaskFinishEvent.name}
            import ${TaskSuccessResult.name}
            import ${TaskFailureResult.name}
            import ${TaskSkippedResult.name}
            import ${BuildServiceParameters.name}

            abstract class LoggingListener implements OperationCompletionListener, BuildService<BuildServiceParameters.None>, Closeable {
                String data

                void configTime(String data) {
                    this.data = data
                }

                @Override
                void onFinish(FinishEvent event) {
                    if (event instanceof TaskFinishEvent) {
                        println("service: finish \${event.descriptor.taskPath} with data=" + data)
                    } else {
                        throw new IllegalArgumentException()
                    }
                }

                @Override
                void close() {
                    println("service: closed with data=" + data)
                }
            }
        """
    }

    def registeringPlugin() {
        buildFile << """
            import ${BuildEventsListenerRegistry.name}

            abstract class LoggingPlugin implements Plugin<Project> {
                @Inject
                abstract BuildEventsListenerRegistry getListenerRegistry()

                void apply(Project project) {
                    def listener = project.gradle.sharedServices.registerIfAbsent("listener", LoggingListener) { }
                    listenerRegistry.onTaskCompletion(listener)
                }
            }
        """
    }
}
