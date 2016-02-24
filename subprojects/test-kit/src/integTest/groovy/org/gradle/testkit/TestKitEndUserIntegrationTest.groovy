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
import org.gradle.integtests.fixtures.daemon.DaemonLogsAnalyzer
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.test.fixtures.file.TestFile
import org.gradle.testkit.runner.GradleRunnerIntegrationTest
import org.gradle.testkit.runner.GradleRunner

import org.gradle.testkit.runner.fixtures.annotations.NoDebug
import org.gradle.testkit.runner.fixtures.annotations.NonCrossVersion
import org.gradle.testkit.runner.internal.DefaultGradleRunner
import org.gradle.testkit.runner.internal.TempTestKitDirProvider
import org.gradle.util.GFileUtils
import org.gradle.util.UsesNativeServices
import spock.lang.Unroll

@NonCrossVersion
@UsesNativeServices
class TestKitEndUserIntegrationTest extends GradleRunnerIntegrationTest {

    def setup() {
        executer.requireGradleHome().withStackTraceChecksDisabled()
        executer.withEnvironmentVars(GRADLE_USER_HOME: executer.gradleUserHomeDir.absolutePath)
        buildFile << buildFileForGroovyProject()
    }

    private TestFile writeTest(String content, String className = 'BuildLogicFunctionalTest') {
        file("src/test/groovy/org/gradle/test/${className}.groovy") << content
    }

    @NoDebug
    def "use of GradleRunner API in test class without declaring test-kit dependency causes compilation error"() {
        given:
        writeTest(buildLogicFunctionalTestCreatingGradleRunner())

        when:
        fails('build')

        then:
        executedAndNotSkipped(':compileTestGroovy')
        failure.assertHasCause('Compilation failed; see the compiler error output for details.')
        failure.error.contains("unable to resolve class $GradleRunner.name")
    }

    @NoDebug
    def "attempt to use implicit gradle version fails if test kit is not being used from a distribution"() {
        def jarsDir = file('jars').createDir()

        new File(distribution.gradleHomeDir, 'lib').eachFileRecurse(FileType.FILES) { f ->
            if (f.name.contains("test-kit")) {
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
    @NoDebug
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
        result.error.contains("unable to resolve class $clazz.name")
        executedAndNotSkipped(':compileTestGroovy')
        assertDaemonsAreStopping()

        cleanup:
        killDaemons()

        where:
        clazz       | origin
        JavaVersion | 'Gradle core'
        IntMath     | 'Google Guava'
    }

    @NoDebug
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
        assertDaemonsAreStopping()

        cleanup:
        killDaemons()
    }

    def "successfully execute functional test and verify expected result"() {
        buildFile << gradleTestKitDependency()
        writeTest successfulSpockTest('BuildLogicFunctionalTest')

        when:
        succeeds('build')

        then:
        executedAndNotSkipped(":test", ":build")
        assertDaemonsAreStopping()

        cleanup:
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

        assertDaemonsAreStopping()

        cleanup:
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
                        .withDebug($debug)
                        .build()

                    then:
                    noExceptionThrown()
                }
            }
        """

        when:
        succeeds('build')

        then:
        executedAndNotSkipped(":test", ":build")
        assertDaemonsAreStopping()

        cleanup:
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
                        .withDebug($debug)
                        .build()

                    then:
                    noExceptionThrown()
                }
            }
        """

        when:
        succeeds('build')

        then:
        executedAndNotSkipped(':test')
        assertDaemonsAreStopping()

        cleanup:
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
                        .withDebug($debug)
                        .build()

                    then:
                    noExceptionThrown()
                }
            }
        """

        when:
        succeeds('build')

        then:
        executedAndNotSkipped(':test')
        assertDaemonsAreStopping()

        cleanup:
        killDaemons()
    }

    def "can test plugin and custom task as external files by using default conventions from Java Gradle plugin development plugin"() {
        file("settings.gradle") << "include 'sub'"
        file("sub/build.gradle") << "apply plugin: 'groovy'; dependencies { compile localGroovy() }"
        file("sub/src/main/groovy/org/gradle/test/lib/Support.groovy") << "package org.gradle.test.lib; class Support { static String MSG = 'Hello world!' }"

        buildFile << """
            apply plugin: 'java-gradle-plugin'

            dependencies {
              compile project(":sub")
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

                def setup() {
                    buildFile = testProjectDir.newFile('build.gradle')
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
                        .withDebug($debug)
                        .build()

                    then:
                    noExceptionThrown()
                }
            }
        """

        when:
        succeeds('build')

        then:
        executedAndNotSkipped(':test')
        assertDaemonsAreStopping()

        cleanup:
        killDaemons()
    }

    def "can test plugin and custom task as external files by configuring custom test source set with Java Gradle plugin development plugin"() {
        file("settings.gradle") << "include 'sub'"
        file("sub/build.gradle") << "apply plugin: 'groovy'; dependencies { compile localGroovy() }"
        file("sub/src/main/groovy/org/gradle/test/lib/Support.groovy") << "package org.gradle.test.lib; class Support { static String MSG = 'Hello world!' }"

        buildFile << """
            apply plugin: 'java-gradle-plugin'

            sourceSets {
                functionalTest {
                    groovy.srcDir file('src/functionalTest/groovy')
                    resources.srcDir file('src/functionalTest/resources')
                    compileClasspath += sourceSets.main.output + configurations.testRuntime
                    runtimeClasspath += output + compileClasspath
                }
            }

            task functionalTest(type: Test) {
                testClassesDir = sourceSets.functionalTest.output.classesDir
                classpath = sourceSets.functionalTest.runtimeClasspath
            }

            check.dependsOn functionalTest

            javaGradlePlugin {
                functionalTestClasspath {
                    testSourceSets sourceSets.functionalTest
                }
            }

            dependencies {
              compile project(":sub")
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

        file("src/functionalTest/groovy/org/gradle/test/BuildLogicFunctionalTest.groovy") << """
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
                        .withDebug($debug)
                        .build()

                    then:
                    noExceptionThrown()
                }
            }
        """

        when:
        succeeds('build')

        then:
        executedAndNotSkipped(':functionalTest')
        assertDaemonsAreStopping()

        cleanup:
        killDaemons()
    }

    def "can control debug mode through system property"() {
        buildFile << gradleTestKitDependency()
        buildFile << """
            test {
                systemProperty '$DefaultGradleRunner.DEBUG_SYS_PROP', '$debug'
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
                    gradleRunner.debug == $debug
                    noExceptionThrown()
                }
            }
        """

        when:
        succeeds('build')

        then:
        executedAndNotSkipped(":test", ":build")
        assertDaemonsAreStopping()

        cleanup:
        killDaemons()
    }

    private DaemonLogsAnalyzer createDaemonLogAnalyzer() {
        File daemonBaseDir = new File(new TempTestKitDirProvider().getDir(), 'daemon')
        DaemonLogsAnalyzer.newAnalyzer(daemonBaseDir, executer.distribution.version.version)
    }

    private void assertDaemonsAreStopping() {
        createDaemonLogAnalyzer().visible*.stops()
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

                        .withDebug($debug)
                        .build()

                    then:
                    noExceptionThrown()
                }
            }
        """
    }

}
