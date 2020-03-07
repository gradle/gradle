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
import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import org.gradle.integtests.fixtures.TestResources
import org.junit.Rule
import spock.lang.Issue
import spock.lang.Unroll

class ExecIntegrationTest extends AbstractIntegrationSpec {
    @Rule
    public final TestResources testResources = new TestResources(testDirectoryProvider)

    @Unroll
    @ToBeFixedForInstantExecution(iterationMatchers = ".*javaexecTask")
    def 'can execute java with #task'() {
        given:
        buildFile << """
            import javax.inject.Inject

            apply plugin: 'java'

            task javaexecTask(type: JavaExec) {
                ext.testFile = file("${'$'}buildDir/${'$'}name")
                classpath(sourceSets.main.output.classesDirs)
                main = 'org.gradle.TestMain'
                args projectDir, testFile
                doLast {
                    assert testFile.exists()
                }
                assert delegate instanceof ExtensionAware
            }

            task javaexecProjectMethod() {
                ext.testFile = file("${'$'}buildDir/${'$'}name")
                dependsOn(sourceSets.main.output)
                doFirst {
                    javaexec {
                        assert !(delegate instanceof ExtensionAware)
                        classpath(sourceSets.main.output.classesDirs)
                        main 'org.gradle.TestMain'
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
                    it.main 'org.gradle.TestMain'
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
    @ToBeFixedForInstantExecution(iterationMatchers = ".*execTask")
    def 'can execute commands with #task'() {
        given:
        buildFile << """
            import org.gradle.internal.jvm.Jvm
            import javax.inject.Inject

            apply plugin: 'java'

            task execTask(type: Exec) {
                dependsOn sourceSets.main.runtimeClasspath
                ext.testFile = file("${'$'}buildDir/${'$'}name")
                executable = Jvm.current().getJavaExecutable()
                args '-cp', sourceSets.main.runtimeClasspath.asPath, 'org.gradle.TestMain', projectDir, testFile
                doLast {
                    assert testFile.exists()
                }
                assert delegate instanceof ExtensionAware
            }

            task execProjectMethod {
                dependsOn sourceSets.main.runtimeClasspath
                ext.testFile = file("${'$'}buildDir/${'$'}name")
                doFirst {
                    exec {
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
            abstract class InjectedServiceTask extends DefaultTask {

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

            task $taskName(type: InjectedServiceTask) {
                dependsOn(sourceSets.main.runtimeClasspath)
            }
        """
    }

    @Issue("GRADLE-3528")
    @ToBeFixedForInstantExecution
    def "when the user declares outputs it becomes incremental"() {
        given:
        buildFile << '''
            apply plugin: 'java'

            task run(type: Exec) {
                inputs.files sourceSets.main.runtimeClasspath
                ext.testFile = file("$buildDir/out.txt")
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

    @ToBeFixedForInstantExecution
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
                ext.testFile = file("$buildDir/out.txt")
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
}
