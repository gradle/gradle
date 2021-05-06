/*
 * Copyright 2010 the original author or authors.
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


package org.gradle.integtests

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.TestResources
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache
import org.junit.Rule
import spock.lang.Issue
import spock.lang.Unroll

class ExecIntegrationTest extends AbstractIntegrationSpec {
    @Rule
    public final TestResources testResources = new TestResources(testDirectoryProvider)

    @Unroll
    @UnsupportedWithConfigurationCache(iterationMatchers = ".*javaexecProjectMethod")
    def 'can execute java with #task'() {
        given:
        buildFile << """
            apply plugin: 'java'

            task javaexecTask(type: JavaExec) {
                def testFile = file("${'$'}buildDir/${'$'}name")
                classpath(sourceSets.main.output.classesDirs)
                mainClass = 'org.gradle.TestMain'
                args projectDir, testFile
                doLast {
                    assert testFile.exists()
                }
                assert delegate instanceof ExtensionAware
            }

            task javaexecProjectMethod() {
                def testFile = file("${'$'}buildDir/${'$'}name")
                dependsOn(sourceSets.main.output)
                doFirst {
                    project.javaexec {
                        assert !(delegate instanceof ExtensionAware)
                        classpath(sourceSets.main.output.classesDirs)
                        mainClass = 'org.gradle.TestMain'
                        args projectDir, testFile
                    }
                }
                doLast {
                    assert testFile.exists()
                }
            }

            ${
            injectedTaskActionTask('javaexecInjectedTaskAction', '''
                File testFile = layout.buildDirectory.file(name).get().asFile
                execOperations.javaexec {
                    assert !(it instanceof ExtensionAware)
                    it.classpath(execClasspath)
                    it.mainClass = 'org.gradle.TestMain'
                    it.args layout.projectDirectory.asFile, testFile
                }
                assert testFile.exists()
            ''')
        }
            javaexecInjectedTaskAction.execClasspath.from(project.sourceSets['main'].output.classesDirs)
        """.stripIndent()

        expect:
        succeeds task

        where:
        task << ['javaexecTask', 'javaexecProjectMethod', 'javaexecInjectedTaskAction']
    }

    @Unroll
    @UnsupportedWithConfigurationCache(iterationMatchers = ".*execProjectMethod")
    def 'can execute commands with #task'() {
        given:
        buildFile << """
            import org.gradle.internal.jvm.Jvm

            apply plugin: 'java'

            task execTask(type: Exec) {
                dependsOn sourceSets.main.runtimeClasspath
                def testFile = file("${'$'}buildDir/${'$'}name")
                executable = Jvm.current().getJavaExecutable()
                args '-cp', sourceSets.main.runtimeClasspath.asPath, 'org.gradle.TestMain', projectDir, testFile
                doLast {
                    assert testFile.exists()
                }
                assert delegate instanceof ExtensionAware
            }

            task execProjectMethod {
                dependsOn sourceSets.main.runtimeClasspath
                def testFile = file("${'$'}buildDir/${'$'}name")
                doFirst {
                    project.exec {
                        executable Jvm.current().getJavaExecutable()
                        args '-cp', sourceSets.main.runtimeClasspath.asPath, 'org.gradle.TestMain', projectDir, testFile
                        assert !(delegate instanceof ExtensionAware)
                    }
                }
                doLast {
                    assert testFile.exists()
                }
            }

            ${
            injectedTaskActionTask('execInjectedTaskAction', '''
                File testFile = layout.buildDirectory.file(name).get().asFile
                execOperations.exec {
                    assert !(it instanceof ExtensionAware)
                    it.executable Jvm.current().getJavaExecutable()
                    it.args '-cp', execClasspath.asPath, 'org.gradle.TestMain', layout.projectDirectory.asFile, testFile
                }
                assert testFile.exists()
            ''')
        }
            execInjectedTaskAction.execClasspath.from(project.sourceSets['main'].runtimeClasspath)
        """.stripIndent()

        expect:
        succeeds task

        where:
        task << ['execTask', 'execProjectMethod', 'execInjectedTaskAction']
    }

    private static String injectedTaskActionTask(String taskName, String taskActionBody) {
        return """
            abstract class InjectedServiceTask_$taskName extends DefaultTask {

                @Classpath
                abstract ConfigurableFileCollection getExecClasspath()

                @Inject
                abstract ProjectLayout getLayout()

                @Inject
                abstract ExecOperations getExecOperations()

                @TaskAction
                void myAction() {
                    $taskActionBody
                }
            }

            task $taskName(type: InjectedServiceTask_$taskName) {
                dependsOn(sourceSets.main.runtimeClasspath)
            }
        """
    }

    @Issue("GRADLE-3528")
    def "when the user declares outputs it becomes incremental"() {
        given:
        buildFile << '''
            apply plugin: 'java'

            task run(type: Exec) {
                inputs.files sourceSets.main.runtimeClasspath
                def testFile = file("$buildDir/out.txt")
                outputs.file testFile
                executable = org.gradle.internal.jvm.Jvm.current().getJavaExecutable()
                args '-cp', sourceSets.main.runtimeClasspath.asPath, 'org.gradle.TestMain', projectDir, testFile
                doLast {
                    assert testFile.exists()
                }
            }
        '''.stripIndent()

        when:
        run "run"

        then:
        executedAndNotSkipped(":run")

        when:
        run "run"

        then:
        skipped(":run")

        when:
        file('build/out.txt').delete()

        and:
        run "run"

        then:
        executedAndNotSkipped(":run")
    }

    def "arguments can be passed by using argument providers"() {
        given:
        buildFile << '''
            apply plugin: 'java'

            class JavaTestCommand implements CommandLineArgumentProvider {
                @Internal
                File expectedWorkingDir

                @Input
                String getExpectedWorkingDirPath() {
                    return expectedWorkingDir.absolutePath
                }

                @Classpath
                FileCollection classPath

                @OutputFile
                File outputFile

                @Override
                Iterable<String> asArguments() {
                    ['-cp', classPath.asPath, 'org.gradle.TestMain', expectedWorkingDirPath, outputFile.absolutePath]
                }
            }

            task run(type: Exec) {
                def testFile = file("$buildDir/out.txt")
                argumentProviders << new JavaTestCommand(
                    expectedWorkingDir: projectDir,
                    classPath: sourceSets.main.runtimeClasspath,
                    outputFile: testFile
                )
                executable = org.gradle.internal.jvm.Jvm.current().getJavaExecutable()
                doLast {
                    assert testFile.exists()
                }
            }
        '''

        when:
        run "run"
        then:
        executedAndNotSkipped ":run"

        when:
        run "run"
        then:
        skipped ":run"

        when:
        file('build/out.txt').delete()
        and:
        run "run"
        then:
        executedAndNotSkipped ":run"
    }

    @Unroll
    @UnsupportedWithConfigurationCache(iterationMatchers = [".*Task", ".*ProjectMethod"])
    def "can capture output of #task"() {

        given:
        buildFile << """
            import org.gradle.internal.jvm.Jvm
            import static org.gradle.util.internal.TextUtil.normaliseFileAndLineSeparators

            apply plugin: 'java'

            // Exec

            task execTask(type: Exec) {
                dependsOn sourceSets.main.runtimeClasspath
                def testFile = file("${'$'}buildDir/${'$'}name")
                executable = Jvm.current().getJavaExecutable()
                args '-cp', sourceSets.main.runtimeClasspath.asPath, 'org.gradle.TestMain', projectDir, testFile
                def output = new ByteArrayOutputStream()
                standardOutput = output
                doLast {
                    assert testFile.exists()
                    assert normaliseFileAndLineSeparators(output.toString()) == "Created file \${normaliseFileAndLineSeparators(testFile.canonicalPath)}\\n"
                }
                assert delegate instanceof ExtensionAware
            }

            task execProjectMethod {
                dependsOn sourceSets.main.runtimeClasspath
                def testFile = file("${'$'}buildDir/${'$'}name")
                doLast {
                    def output = new ByteArrayOutputStream()
                    project.exec {
                        executable Jvm.current().getJavaExecutable()
                        args '-cp', sourceSets.main.runtimeClasspath.asPath, 'org.gradle.TestMain', projectDir, testFile
                        standardOutput = output
                        assert !(delegate instanceof ExtensionAware)
                    }
                    assert testFile.exists()
                    assert normaliseFileAndLineSeparators(output.toString()) == "Created file \${normaliseFileAndLineSeparators(testFile.canonicalPath)}\\n"
                }
            }

            ${
            injectedTaskActionTask('execInjectedTaskAction', '''
                File testFile = layout.buildDirectory.file(name).get().asFile
                def output = new ByteArrayOutputStream()
                execOperations.exec {
                    assert !(it instanceof ExtensionAware)
                    it.executable Jvm.current().getJavaExecutable()
                    it.args '-cp', execClasspath.asPath, 'org.gradle.TestMain', layout.projectDirectory.asFile, testFile
                    it.standardOutput = output
                }
                assert testFile.exists()
                assert normaliseFileAndLineSeparators(output.toString()) == "Created file \${normaliseFileAndLineSeparators(testFile.canonicalPath)}\\n"
            ''')
        }
            execInjectedTaskAction.execClasspath.from(project.sourceSets['main'].runtimeClasspath)

            // JavaExec

            task javaexecTask(type: JavaExec) {
                def testFile = file("${'$'}buildDir/${'$'}name")
                classpath(sourceSets.main.output.classesDirs)
                mainClass = 'org.gradle.TestMain'
                args projectDir, testFile
                def output = new ByteArrayOutputStream()
                standardOutput = output
                doLast {
                    assert testFile.exists()
                    assert normaliseFileAndLineSeparators(output.toString()) == "Created file \${normaliseFileAndLineSeparators(testFile.canonicalPath)}\\n"
                }
                assert delegate instanceof ExtensionAware
            }

            task javaexecProjectMethod() {
                def testFile = file("${'$'}buildDir/${'$'}name")
                dependsOn(sourceSets.main.output)
                doLast {
                    def output = new ByteArrayOutputStream()
                    project.javaexec {
                        assert !(delegate instanceof ExtensionAware)
                        classpath(sourceSets.main.output.classesDirs)
                        mainClass = 'org.gradle.TestMain'
                        args projectDir, testFile
                        standardOutput = output
                    }
                    assert testFile.exists()
                    assert normaliseFileAndLineSeparators(output.toString()) == "Created file \${normaliseFileAndLineSeparators(testFile.canonicalPath)}\\n"
                }
            }

            ${
            injectedTaskActionTask('javaexecInjectedTaskAction', '''
                File testFile = layout.buildDirectory.file(name).get().asFile
                def output = new ByteArrayOutputStream()
                execOperations.javaexec {
                    assert !(it instanceof ExtensionAware)
                    it.classpath(execClasspath)
                    it.mainClass = 'org.gradle.TestMain'
                    it.args layout.projectDirectory.asFile, testFile
                    it.standardOutput = output
                }
                assert testFile.exists()
                assert normaliseFileAndLineSeparators(output.toString()) == "Created file \${normaliseFileAndLineSeparators(testFile.canonicalPath)}\\n"
            ''')
        }
            javaexecInjectedTaskAction.execClasspath.from(project.sourceSets['main'].output.classesDirs)

        """.stripIndent()

        expect:
        succeeds task

        where:
        task << [
            'execTask', 'execProjectMethod', 'execInjectedTaskAction',
            'javaexecTask', 'javaexecProjectMethod', 'javaexecInjectedTaskAction'
        ]
    }

    def "execResult property is deprecated"() {
        when:
        buildFile << """
            task run(type: Exec) {
                executable = org.gradle.internal.jvm.Jvm.current().getJavaExecutable()
                args("-version")
                doLast {
                    println(execResult)
                }
            }
        """
        executer.expectDocumentedDeprecationWarning("The AbstractExecTask.execResult property has been deprecated. This is scheduled to be removed in Gradle 8.0. Please use the executionResult property instead. See https://docs.gradle.org/current/dsl/org.gradle.api.tasks.AbstractExecTask.html#org.gradle.api.tasks.AbstractExecTask:execResult for more details.")

        then:
        succeeds("run")
    }
}
