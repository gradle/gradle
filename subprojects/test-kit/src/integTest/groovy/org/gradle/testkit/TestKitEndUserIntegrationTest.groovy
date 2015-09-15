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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.daemon.DaemonLogsAnalyzer
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.test.fixtures.file.TestFile
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.internal.TempTestKitDirProvider
import org.gradle.util.GFileUtils

class TestKitEndUserIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        executer.requireGradleHome().withStackTraceChecksDisabled()
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

    def "use of GradleRunner API in test class by depending on external test-kit dependency causes compilation error"() {
        File gradleDistPluginsDir = new File(distribution.gradleHomeDir, 'lib/plugins')
        File[] gradleTestKitLibs = gradleDistPluginsDir.listFiles(new GradleTestKitJarFilenameFilter())
        assert gradleTestKitLibs.length == 1
        File gradleTestKitLib = gradleTestKitLibs[0]
        TestFile libDir = testDirectoryProvider.createDir('lib')
        GFileUtils.copyFile(gradleTestKitLib, new File(libDir, gradleTestKitLib.name))

        buildFile << libDirDependency()
        writeTest(buildLogicFunctionalTestCreatingGradleRunner())

        when:
        fails('build')

        then:
        executedAndNotSkipped(':compileTestGroovy')
        failure.error.contains("Unable to load class $GradleRunner.name due to missing dependency")
    }

    def "creating GradleRunner instance by depending on Gradle libraries outside of Gradle distribution throws exception"() {
        File gradleDistLibDir = new File(distribution.gradleHomeDir, 'lib')
        File gradleDistPluginsDir = new File(gradleDistLibDir, 'plugins')
        File[] gradleCoreLibs = gradleDistLibDir.listFiles(new GradleCoreJarFilenameFilter())
        File[] gradleTestKitLibs = gradleDistPluginsDir.listFiles(new GradleTestKitJarFilenameFilter())
        assert gradleCoreLibs.length == 3
        assert gradleTestKitLibs.length == 1
        File[] allGradleLibs = gradleCoreLibs + gradleTestKitLibs
        TestFile libDir = testDirectoryProvider.createDir('lib')

        allGradleLibs.each {
            GFileUtils.copyFile(it, new File(libDir, it.name))
        }

        buildFile << libDirDependency()
        writeTest(buildLogicFunctionalTestCreatingGradleRunner())

        when:
        fails('build')

        then:
        executedAndNotSkipped(':test')
        failure.output.contains('java.lang.IllegalStateException: Could not create a GradleRunner, as the GradleRunner class was not loaded from a Gradle distribution')
    }

    def "successfully execute functional test and verify expected result"() {
        buildFile << gradleTestKitDependency()
        writeTest successfulSpockTest('BuildLogicFunctionalTest')

        when:
        succeeds('build')

        then:
        executedAndNotSkipped(":test", ":build")
        assertDaemonsAreStopping()
    }

    def "successfully execute functional tests with parallel forks"() {
        buildFile << gradleTestKitDependency()
        buildFile << """
            test {
                maxParallelForks = 3

                testLogging {
                    events 'started'
                }
            }
        """

        def testClassNames = (1..10).collect { "BuildLogicFunctionalTest$it" }

        testClassNames.each { testClassName ->
            writeTest successfulSpockTest(testClassName), testClassName
        }

        when:
        ExecutionResult result = succeeds('build')

        then:
        executedAndNotSkipped(":test", ":build")

        testClassNames.each { testClassName ->
            assert result.assertOutputContains("org.gradle.test.${testClassName} > execute helloWorld task STARTED")
        }

        assertDaemonsAreStopping()
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
                        .build()

                    then:
                    result.standardOutput.contains('Hello world!')
                    result.standardError == ''
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
        assertDaemonsAreStopping()
    }

    def "functional test fails due to invalid JVM parameter for test execution"() {
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
        assertDaemonsAreStopping()
    }

    @LeaksFileHandles
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
                        .build()

                    then:
                    result.standardOutput.contains('Hello world!')
                    result.standardError == ''
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
        assertDaemonsAreStopping()
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
                List<URI> pluginClasspath

                def setup() {
                    buildFile = testProjectDir.newFile('build.gradle')
                    pluginClasspath = getClass().classLoader.findResource("plugin-classpath.txt")
                      .readLines()
                      .collect { new File(it).toURI() }
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
                        .withClasspath(pluginClasspath)
                        .build()

                    then:
                    result.standardOutput.contains('Hello world!')
                    result.standardOutput.contains('Bye world!')
                    result.standardError == ''
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
        assertDaemonsAreStopping()
    }

    private DaemonLogsAnalyzer createDaemonLogAnalyzer() {
        File daemonBaseDir = new File(new TempTestKitDirProvider().getDir(), 'daemon')
        DaemonLogsAnalyzer.newAnalyzer(daemonBaseDir, executer.distribution.version.version)
    }

    private void assertDaemonsAreStopping() {
        createDaemonLogAnalyzer().visible*.stops()
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

    private static String libDirDependency() {
        """
            dependencies {
                testCompile fileTree(dir: 'lib', include: '*.jar')
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
                    GradleRunner.create()
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
                        .build()

                    then:
                    result.standardOutput.contains('Hello world!')
                    result.standardError == ''
                    result.taskPaths(SUCCESS) == [':helloWorld']
                    result.taskPaths(SKIPPED).empty
                    result.taskPaths(UP_TO_DATE).empty
                    result.taskPaths(FAILED).empty
                }
            }
        """
    }

    private class GradleTestKitJarFilenameFilter implements FilenameFilter {
        boolean accept(File dir, String name) {
            name.startsWith('gradle-test-kit')
        }
    }

    private class GradleCoreJarFilenameFilter implements FilenameFilter {
        boolean accept(File dir, String name) {
            name.startsWith('gradle-core') || name.startsWith('gradle-base-services')
        }
    }
}
