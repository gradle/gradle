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

package org.gradle.testkit

import com.google.common.math.IntMath
import groovy.io.FileType
import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.daemon.DaemonLogsAnalyzer
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.test.fixtures.file.TestFile
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.fixtures.GradleRunnerIntegTestRunner
import org.gradle.testkit.runner.internal.DefaultGradleRunner
import org.gradle.testkit.runner.internal.TempTestKitDirProvider
import org.gradle.util.GFileUtils
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.runner.RunWith
import spock.lang.Unroll

@RunWith(GradleRunnerIntegTestRunner)
class TestKitEndUserIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        executer.requireGradleHome().withStackTraceChecksDisabled()
        executer.withEnvironmentVars(GRADLE_USER_HOME: executer.gradleUserHomeDir.absolutePath)
        buildFile << buildFileForGroovyProject()
    }

    def "use of GradleRunner API in test class without declaring test-kit dependency causes compilation error"() {
        given:
        writeTest(buildLogicFunctionalTestCreatingGradleRunner())

        when:
        fails('build')

        then:
        executedAndNotSkipped(':compileTestGroovy')
        failureHasCause('Compilation failed; see the compiler error output for details.')
        failure.error.contains("unable to resolve class $GradleRunner.name")
    }

    private TestFile writeTest(String content, String className = 'BuildLogicFunctionalTest') {
        testDirectoryProvider.file("src/test/groovy/org/gradle/test/${className}.groovy") << content
    }

    def "attempt to use implicit gradle version fails if test kit is not being used from a distribution"() {
        def jarsDir = testDirectoryProvider.createDir('jars')

        new File(distribution.gradleHomeDir, 'lib').eachFileRecurse(FileType.FILES) { f ->
            if (["test-kit"].any { f.name.contains it }) {
                GFileUtils.copyFile(f, new File(jarsDir, f.name))
            }
        }

        def testKitJar = jarsDir.listFiles().find { it.name.contains "test-kit" }

        buildFile << """
            dependencies {
                testCompile fileTree(dir: 'jars', include: '*.jar')
            }
        """

        writeTest(buildLogicFunctionalTestCreatingGradleRunner())

        when:
        fails('build')

        then:
        failure.output.contains("Could not find a Gradle runtime to use based on the location of the GradleRunner class: $testKitJar.canonicalPath. Please specify a Gradle runtime to use via GradleRunner.withGradleVersion() or similar.")
    }

    @Unroll
    def "attempt to use #origin class in functional test should fail"() {
        buildFile << gradleTestKitDependency()
        writeTest """
            package org.gradle.test

            import $clazz.name
            import spock.lang.Specification

            class BuildLogicFunctionalTest extends Specification {}
        """

        when:
        fails('build')

        then:
        errorOutput.contains("unable to resolve class $clazz.name")
        executedAndNotSkipped(':compileTestGroovy')
        killDaemons()

        where:
        clazz       | origin
        JavaVersion | 'Gradle core'
        IntMath     | 'Google Guava'
    }

    def "class from user-defined library doesn't conflict with same Gradle core library in runtime classpath"() {
        buildFile << gradleTestKitDependency()
        buildFile << """
            dependencies {
                testCompile 'com.google.guava:guava-jdk5:13.0'
            }
        """
        writeTest """
            package org.gradle.test

            import $IntMath.name
            import spock.lang.Specification

            class BuildLogicFunctionalTest extends Specification {}
        """

        when:
        succeeds('build')

        then:
        executedAndNotSkipped(":test", ":build")
        killDaemons()
    }

    def "successfully execute functional test and verify expected result"() {
        buildFile << gradleTestKitDependency()
        writeTest successfulSpockTest('BuildLogicFunctionalTest')

        when:
        succeeds('build')

        then:
        executedAndNotSkipped(":test", ":build")
        killDaemons()
    }

    def "successfully execute functional tests with parallel forks"() {
        buildFile << gradleTestKitDependency()
        buildFile << parallelTests()

        def testClassNames = (1..10).collect { "BuildLogicFunctionalTest$it" }

        testClassNames.each { testClassName ->
            writeTest successfulSpockTest(testClassName), testClassName
        }

        when:
        ExecutionResult result = succeeds('build')

        then:
        executedAndNotSkipped(":test", ":build")

        testClassNames.each { testClassName ->
            result.assertOutputContains("org.gradle.test.${testClassName} > execute helloWorld task STARTED")
        }

        killDaemons()
    }

    def "successfully execute functional test with custom Gradle user home directory"() {
        buildFile << gradleTestKitDependency()
        writeTest """
            package org.gradle.test

            import org.gradle.testkit.runner.GradleRunner
            import static org.gradle.testkit.runner.TaskOutcome.*
            import org.junit.Rule
            import org.junit.rules.TemporaryFolder
            import spock.lang.Specification

            class BuildLogicFunctionalTest extends Specification {
                @Rule final TemporaryFolder testProjectDir = new TemporaryFolder()
                @Rule final TemporaryFolder testGradleUserHomeDir = new TemporaryFolder()

                File buildFile

                def setup() {
                    buildFile = testProjectDir.newFile('build.gradle')
                }

                def "execute helloWorld task"() {
                    given:
                    buildFile << '''
                        task helloWorld {
                            doLast {
                                println 'Hello world!'
                            }
                        }
                    '''

                    when:
                    def result = GradleRunner.create()
                        .withProjectDir(testProjectDir.root)
                        .withArguments('helloWorld')
                        .withTestKitDir(testGradleUserHomeDir.root)
                        .withDebug($GradleRunnerIntegTestRunner.debug)
                        .build()

                    then:
                    result.output.contains('Hello world!')
                    result.taskPaths(SUCCESS) == [':helloWorld']
                    result.taskPaths(SKIPPED).empty
                    result.taskPaths(UP_TO_DATE).empty
                    result.taskPaths(FAILED).empty
                }
            }
        """

        when:
        succeeds('build')

        then:
        executedAndNotSkipped(":test", ":build")
        killDaemons()
    }

    def "functional test fails due to invalid JVM parameter for test execution"() {
        buildFile << gradleTestKitDependency() << "test { testLogging { showCauses true } }"
        writeTest """
            package org.gradle.test

            import org.gradle.testkit.runner.GradleRunner
            import static org.gradle.testkit.runner.TaskOutcome.*
            import org.junit.Rule
            import org.junit.rules.TemporaryFolder
            import spock.lang.Specification

            class BuildLogicFunctionalTest extends Specification {
                @Rule final TemporaryFolder testProjectDir = new TemporaryFolder()
                File buildFile

                def setup() {
                    buildFile = testProjectDir.newFile('build.gradle')
                    new File(testProjectDir.root, 'gradle.properties') << 'org.gradle.jvmargs=-unknown'
                }

                def "execute helloWorld task"() {
                    given:
                    buildFile << '''
                        task helloWorld {
                            doLast {
                                println 'Hello world!'
                            }
                        }
                    '''

                    expect:
                    GradleRunner.create()
                        .withProjectDir(testProjectDir.root)
                        .withArguments('helloWorld')
                        .build()
                }
            }
        """

        when:
        fails('build')

        then:
        failureDescriptionContains("Execution failed for task ':test'.")
        // IBM JVM produces a slightly different error message
        failure.output.contains('Unrecognized option: -unknown') || failure.output.contains('Command-line option unrecognised: -unknown')
        killDaemons()
    }

    def "can test plugin and custom task as external files by adding them to the build script's classpath"() {
        file("settings.gradle") << "include 'sub'"
        file("sub/build.gradle") << "apply plugin: 'groovy'; dependencies { compile localGroovy() }"
        file("sub/src/main/groovy/org/gradle/test/lib/Support.groovy") << "package org.gradle.test.lib; class Support { static String MSG = 'Hello world!' }"

        buildFile <<
            gradleApiDependency() <<
            gradleTestKitDependency() <<
            """
                dependencies {
                  compile project(":sub")
                }

                task createClasspathManifest {
                    def outputDir = file("\$buildDir/\$name")

                    inputs.files sourceSets.main.runtimeClasspath
                    outputs.dir outputDir

                    doLast {
                        outputDir.mkdirs()
                        file("\$outputDir/plugin-classpath.txt").text = sourceSets.main.runtimeClasspath.join("\\n")
                    }
                }

                dependencies {
                    testCompile files(createClasspathManifest)
                }
            """

        file("src/main/groovy/org/gradle/test/HelloWorldPlugin.groovy") << """
            package org.gradle.test

            import org.gradle.api.Plugin
            import org.gradle.api.Project

            class HelloWorldPlugin implements Plugin<Project> {
                void apply(Project project) {
                    project.task('helloWorld', type: HelloWorld)
                }
            }
        """

        file("src/main/groovy/org/gradle/test/HelloWorld.groovy") << """
            package org.gradle.test

            import org.gradle.api.DefaultTask
            import org.gradle.api.tasks.TaskAction
            import org.gradle.test.lib.Support

            class HelloWorld extends DefaultTask {
                @TaskAction
                void doSomething() {
                    println Support.MSG
                }
            }
        """

        writeTest """
            package org.gradle.test

            import org.gradle.testkit.runner.GradleRunner
            import static org.gradle.testkit.runner.TaskOutcome.*
            import org.junit.Rule
            import org.junit.rules.TemporaryFolder
            import spock.lang.Specification

            class BuildLogicFunctionalTest extends Specification {
                @Rule final TemporaryFolder testProjectDir = new TemporaryFolder()
                File buildFile

                def setup() {
                    buildFile = testProjectDir.newFile('build.gradle')
                    def pluginClasspath = getClass().classLoader.findResource("plugin-classpath.txt")
                      .readLines()
                      .collect { it.replace('\\\\', '\\\\\\\\') } // escape backslashes in Windows paths
                      .collect { "'\$it'" }
                      .join(", ")

                    buildFile << \"\"\"
                        buildscript {
                            dependencies {
                                classpath files(\$pluginClasspath)
                            }
                        }
                    \"\"\"
                }

                def "execute helloWorld task"() {
                    given:
                    buildFile << 'apply plugin: org.gradle.test.HelloWorldPlugin'

                    when:
                    def result = GradleRunner.create()
                        .withProjectDir(testProjectDir.root)
                        .withArguments('helloWorld')
                        .withDebug($GradleRunnerIntegTestRunner.debug)
                        .build()

                    then:
                    result.output.contains('Hello world!')
                    result.taskPaths(SUCCESS) == [':helloWorld']
                    result.taskPaths(SKIPPED).empty
                    result.taskPaths(UP_TO_DATE).empty
                    result.taskPaths(FAILED).empty
                }
            }
        """

        when:
        succeeds('build')

        then:
        executedAndNotSkipped(':test')
        killDaemons()
    }

    def "can test plugin and custom task as external files by providing them as classpath through GradleRunner API"() {
        file("settings.gradle") << "include 'sub'"
        file("sub/build.gradle") << "apply plugin: 'groovy'; dependencies { compile localGroovy() }"
        file("sub/src/main/groovy/org/gradle/test/lib/Support.groovy") << "package org.gradle.test.lib; class Support { static String MSG = 'Hello world!' }"

        buildFile <<
            gradleApiDependency() <<
            gradleTestKitDependency() <<
            """
                dependencies {
                  compile project(":sub")
                }

                task createClasspathManifest {
                    def outputDir = file("\$buildDir/\$name")

                    inputs.files sourceSets.main.runtimeClasspath
                    outputs.dir outputDir

                    doLast {
                        outputDir.mkdirs()
                        file("\$outputDir/plugin-classpath.txt").text = sourceSets.main.runtimeClasspath.join("\\n")
                    }
                }

                dependencies {
                    testCompile files(createClasspathManifest)
                }
            """

        file("src/main/groovy/org/gradle/test/HelloWorldPlugin.groovy") << """
            package org.gradle.test

            import org.gradle.api.Plugin
            import org.gradle.api.Project

            class HelloWorldPlugin implements Plugin<Project> {
                void apply(Project project) {
                    project.task('helloWorld', type: HelloWorld)
                }
            }
        """

        file("src/main/groovy/org/gradle/test/HelloWorld.groovy") << """
            package org.gradle.test

            import org.gradle.api.DefaultTask
            import org.gradle.api.tasks.TaskAction
            import org.gradle.test.lib.Support

            class HelloWorld extends DefaultTask {
                @TaskAction
                void doSomething() {
                    println Support.MSG
                }
            }
        """

        file("src/main/groovy/org/gradle/test/ByeWorld.groovy") << """
            package org.gradle.test

            import org.gradle.api.DefaultTask
            import org.gradle.api.tasks.TaskAction

            class ByeWorld extends DefaultTask {
                @TaskAction
                void doSomething() {
                    println 'Bye world!'
                }
            }
        """

        file("src/main/resources/META-INF/gradle-plugins/com.company.helloworld.properties") << """
            implementation-class=org.gradle.test.HelloWorldPlugin
        """

        writeTest """
            package org.gradle.test

            import org.gradle.testkit.runner.GradleRunner
            import static org.gradle.testkit.runner.TaskOutcome.*
            import org.junit.Rule
            import org.junit.rules.TemporaryFolder
            import spock.lang.Specification

            class BuildLogicFunctionalTest extends Specification {
                @Rule final TemporaryFolder testProjectDir = new TemporaryFolder()
                File buildFile
                List<File> pluginClasspath

                def setup() {
                    buildFile = testProjectDir.newFile('build.gradle')
                    pluginClasspath = getClass().classLoader.findResource("plugin-classpath.txt")
                      .readLines()
                      .collect { new File(it) }
                }

                def "execute helloWorld task"() {
                    given:
                    buildFile << \"\"\"
                        plugins {
                            id 'com.company.helloworld'
                        }

                        import org.gradle.test.ByeWorld

                        task byeWorld(type: ByeWorld)
                    \"\"\"

                    when:
                    def result = GradleRunner.create()
                        .withProjectDir(testProjectDir.root)
                        .withArguments('helloWorld', 'byeWorld')
                        .withPluginClasspath(pluginClasspath)
                        .withDebug($GradleRunnerIntegTestRunner.debug)
                        .build()

                    then:
                    result.output.contains('Hello world!')
                    result.output.contains('Bye world!')
                    result.taskPaths(SUCCESS) == [':helloWorld', ':byeWorld']
                    result.taskPaths(SKIPPED).empty
                    result.taskPaths(UP_TO_DATE).empty
                    result.taskPaths(FAILED).empty
                }
            }
        """

        when:
        succeeds('build')

        then:
        executedAndNotSkipped(':test')
        killDaemons()
    }

    def "can control debug mode through system property"() {
        buildFile << gradleTestKitDependency()
        buildFile << """
            test {
                systemProperty '$DefaultGradleRunner.DEBUG_SYS_PROP', '$GradleRunnerIntegTestRunner.debug'
            }
        """
        writeTest """
            package org.gradle.test

            import org.gradle.testkit.runner.GradleRunner
            import static org.gradle.testkit.runner.TaskOutcome.*
            import org.junit.Rule
            import org.junit.rules.TemporaryFolder
            import spock.lang.Specification

            class BuildLogicFunctionalTest extends Specification {
                @Rule final TemporaryFolder testProjectDir = new TemporaryFolder()
                File buildFile

                def setup() {
                    buildFile = testProjectDir.newFile('build.gradle')
                }

                def "execute helloWorld task"() {
                    given:
                    buildFile << '''
                        task helloWorld {
                            doLast {
                                println 'Hello world!'
                            }
                        }
                    '''

                    when:
                    def gradleRunner = GradleRunner.create()
                        .withProjectDir(testProjectDir.root)
                        .withArguments('helloWorld')
                    def result = gradleRunner.build()

                    then:
                    gradleRunner.debug == $GradleRunnerIntegTestRunner.debug
                    result.output.contains('Hello world!')
                    result.taskPaths(SUCCESS) == [':helloWorld']
                    result.taskPaths(SKIPPED).empty
                    result.taskPaths(UP_TO_DATE).empty
                    result.taskPaths(FAILED).empty
                }
            }
        """

        when:
        succeeds('build')

        then:
        executedAndNotSkipped(":test", ":build")
        killDaemons()
    }

    @Requires([TestPrecondition.ONLINE, TestPrecondition.JDK8_OR_EARLIER])
    def "can provide a series of version-based Gradle distributions to execute test"() {
        buildFile << gradleTestKitDependency()
        writeTest """
            package org.gradle.test

            import org.gradle.testkit.runner.GradleRunner
            import static org.gradle.testkit.runner.TaskOutcome.*
            import org.junit.Rule
            import org.junit.rules.TemporaryFolder
            import spock.lang.Specification

            class BuildLogicFunctionalTest extends Specification {
                @Rule final TemporaryFolder testProjectDir = new TemporaryFolder()
                File buildFile

                def setup() {
                    buildFile = testProjectDir.newFile('build.gradle')
                }

                def "execute helloWorld task"() {
                    given:
                    buildFile << '''
                        task helloWorld {
                            doLast {
                                logger.quiet 'Hello world!'
                            }
                        }
                    '''

                    when:
                    def gradleRunner = GradleRunner.create()
                        .withGradleVersion("$gradleVersion")
                        .withProjectDir(testProjectDir.root)
                        .withArguments('helloWorld')
                        .withDebug($GradleRunnerIntegTestRunner.debug)
                    def result = gradleRunner.build()

                    then:
                    result.output.contains('Hello world!')
                    result.taskPaths(SUCCESS) == [':helloWorld']
                    result.taskPaths(SKIPPED).empty
                    result.taskPaths(UP_TO_DATE).empty
                    result.taskPaths(FAILED).empty
                }
            }
        """

        when:
        succeeds('build')

        then:
        executedAndNotSkipped(":test", ":build")
        killDaemons()

        where:
        gradleVersion << ['2.6', '2.7']
    }

    @Requires([TestPrecondition.ONLINE, TestPrecondition.JDK8_OR_EARLIER])
    def "successfully execute functional tests with parallel forks for multiple Gradle distributions"() {
        buildFile << gradleTestKitDependency()
        buildFile << parallelTests()

        def testClassNames = (1..10).collect { "BuildLogicFunctionalTest$it" }

        testClassNames.each { testClassName ->
            writeTest """
                package org.gradle.test

                import org.gradle.testkit.runner.GradleRunner
                import static org.gradle.testkit.runner.TaskOutcome.*
                import org.junit.Rule
                import org.junit.rules.TemporaryFolder
                import spock.lang.Specification

                class $testClassName extends Specification {
                    @Rule final TemporaryFolder testProjectDir = new TemporaryFolder()
                    File buildFile

                    def setup() {
                        buildFile = testProjectDir.newFile('build.gradle')
                    }

                    def "execute helloWorld task"() {
                        given:
                        buildFile << '''
                            task helloWorld {
                                doLast {
                                    logger.quiet 'Hello world!'
                                }
                            }
                        '''

                        when:
                        def gradleRunner = GradleRunner.create()
                            .withGradleVersion("$gradleVersion")
                            .withProjectDir(testProjectDir.root)
                            .withArguments('helloWorld')
                            .withDebug($GradleRunnerIntegTestRunner.debug)
                        def result = gradleRunner.build()

                        then:
                        result.output.contains('Hello world!')
                        result.taskPaths(SUCCESS) == [':helloWorld']
                        result.taskPaths(SKIPPED).empty
                        result.taskPaths(UP_TO_DATE).empty
                        result.taskPaths(FAILED).empty
                    }
                }
            """, testClassName
        }

        when:
        ExecutionResult result = succeeds('build')

        then:
        executedAndNotSkipped(":test", ":build")

        testClassNames.each { testClassName ->
            result.assertOutputContains("org.gradle.test.${testClassName} > execute helloWorld task STARTED")
        }

        killDaemons()

        where:
        gradleVersion << ['2.6', '2.7']
    }

    def "can test settings plugin as external files by adding them to the build script's classpath"() {
        buildFile <<
            gradleApiDependency() <<
            gradleTestKitDependency() <<
            """
                task createClasspathManifest {
                    def outputDir = file("\$buildDir/\$name")

                    inputs.files sourceSets.main.runtimeClasspath
                    outputs.dir outputDir

                    doLast {
                        outputDir.mkdirs()
                        file("\$outputDir/plugin-classpath.txt").text = sourceSets.main.runtimeClasspath.join("\\n")
                    }
                }

                dependencies {
                    testCompile files(createClasspathManifest)
                }
            """

        file("src/main/groovy/org/gradle/test/HelloWorldSettingsPlugin.groovy") << """
            package org.gradle.test

            import org.gradle.api.Plugin
            import org.gradle.api.initialization.Settings

            class HelloWorldSettingsPlugin implements Plugin<Settings> {
                void apply(Settings settings) {
                    println 'Hello world!'
                }
            }
        """

        writeTest """
            package org.gradle.test

            import org.gradle.testkit.runner.GradleRunner
            import static org.gradle.testkit.runner.TaskOutcome.*
            import org.junit.Rule
            import org.junit.rules.TemporaryFolder
            import spock.lang.Specification

            class BuildLogicFunctionalTest extends Specification {
                @Rule final TemporaryFolder testProjectDir = new TemporaryFolder()
                File buildFile
                File settingsFile

                def setup() {
                    buildFile = testProjectDir.newFile('build.gradle')
                    settingsFile = testProjectDir.newFile('settings.gradle')
                    def pluginClasspath = getClass().classLoader.findResource("plugin-classpath.txt")
                      .readLines()
                      .collect { it.replace('\\\\', '\\\\\\\\') } // escape backslashes in Windows paths
                      .collect { "'\$it'" }
                      .join(", ")

                    settingsFile << \"\"\"
                        buildscript {
                            dependencies {
                                classpath files(\$pluginClasspath)
                            }
                        }
                    \"\"\"
                }

                def "apply settings plugin"() {
                    given:
                    settingsFile << 'apply plugin: org.gradle.test.HelloWorldSettingsPlugin'

                    when:
                    def result = GradleRunner.create()
                        .withProjectDir(testProjectDir.root)
                        .withArguments('tasks')
                        .withDebug($GradleRunnerIntegTestRunner.debug)
                        .build()

                    then:
                    result.output.contains('Hello world!')
                    result.taskPaths(SUCCESS) == [':tasks']
                    result.taskPaths(SKIPPED).empty
                    result.taskPaths(UP_TO_DATE).empty
                    result.taskPaths(FAILED).empty
                }
            }
        """

        when:
        succeeds('build')

        then:
        executedAndNotSkipped(':test')
        killDaemons()
    }

    private DaemonLogsAnalyzer createDaemonLogAnalyzer() {
        File daemonBaseDir = new File(new TempTestKitDirProvider().getDir(), 'daemon')
        DaemonLogsAnalyzer.newAnalyzer(daemonBaseDir, executer.distribution.version.version)
    }

    private void killDaemons() {
        createDaemonLogAnalyzer().killAll()
    }

    private static String buildFileForGroovyProject() {
        """
            apply plugin: 'groovy'

            dependencies {
                compile localGroovy()
                testCompile('org.spockframework:spock-core:1.0-groovy-2.4') {
                    exclude module: 'groovy-all'
                }
            }

            repositories {
                mavenCentral()
            }

            test.testLogging.exceptionFormat = 'full'
        """
    }

    private static String gradleTestKitDependency() {
        """
            dependencies {
                testCompile gradleTestKit()
            }
        """
    }

    private static String gradleApiDependency() {
        """
            dependencies {
                compile gradleApi()
            }
        """
    }


    private static String parallelTests() {
        """
            test {
                maxParallelForks = 3

                testLogging {
                    events 'started'
                }
            }
        """
    }

    private static String buildLogicFunctionalTestCreatingGradleRunner() {
        """
            package org.gradle.test

            import org.gradle.testkit.runner.GradleRunner
            import spock.lang.Specification

            class BuildLogicFunctionalTest extends Specification {
                def "create GradleRunner"() {
                    expect:
                    GradleRunner.create().withProjectDir(new File("foo")).build()
                }
            }
        """
    }

    private static String successfulSpockTest(String className) {
        """
            package org.gradle.test

            import org.gradle.testkit.runner.GradleRunner
            import static org.gradle.testkit.runner.TaskOutcome.*
            import org.junit.Rule
            import org.junit.rules.TemporaryFolder
            import spock.lang.Specification

            class $className extends Specification {
                @Rule final TemporaryFolder testProjectDir = new TemporaryFolder()
                File buildFile

                def setup() {
                    buildFile = testProjectDir.newFile('build.gradle')
                }

                def "execute helloWorld task"() {
                    given:
                    buildFile << '''
                        task helloWorld {
                            doLast {
                                println 'Hello world!'
                            }
                        }
                    '''

                    when:
                    def result = GradleRunner.create()
                        .withProjectDir(testProjectDir.root)
                        .withArguments('helloWorld')
                        .withDebug($GradleRunnerIntegTestRunner.debug)
                        .build()

                    then:
                    result.output.contains('Hello world!')
                    result.taskPaths(SUCCESS) == [':helloWorld']
                    result.taskPaths(SKIPPED).empty
                    result.taskPaths(UP_TO_DATE).empty
                    result.taskPaths(FAILED).empty
                }
            }
        """
    }

}
