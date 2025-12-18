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

import org.apache.http.HttpResponse
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClientBuilder
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.TestResources
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache
import org.gradle.integtests.fixtures.daemon.DaemonClientFixture
import org.gradle.process.TestExecHttpServer
import org.gradle.process.TestJavaMain
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.util.internal.TextUtil
import org.junit.Rule
import spock.lang.Issue

import java.util.concurrent.TimeUnit

class ExecIntegrationTest extends AbstractIntegrationSpec {
    @Rule
    public final TestResources testResources = new TestResources(testDirectoryProvider)

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
        task << ['javaexecTask', 'javaexecInjectedTaskAction']
    }

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
        task << ['execTask', 'execInjectedTaskAction']
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

    @UnsupportedWithConfigurationCache(iterationMatchers = [".*Task"], because = "Uses ByteArrayOutputStream to capture task output")
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
            'execTask', 'execInjectedTaskAction',
            'javaexecTask','javaexecInjectedTaskAction'
        ]
    }

    @Issue("https://github.com/gradle/gradle/issues/31282")
    @Requires(UnitTestPreconditions.NotWindows)
    def "running multiple tasks that fork processes is multi-thread safe"() {
        def numOfProjects = 1000
        numOfProjects.times {
            settingsFile << """
                include 'project$it'
            """
            file("project${it}/build.gradle") << """
                abstract class MyExec extends DefaultTask {
                    @Inject
                    abstract ExecOperations getExecOperations()

                    @TaskAction
                    void doIt() {
                        def script = new File(temporaryDir, "script.sh")
                        script.text = "#!/bin/bash"
                        script.executable = true
                        execOperations.exec {
                            commandLine script.absolutePath
                        }
                        script.delete()
                    }
                }
                tasks.register("run", MyExec)
            """
        }
        expect:
        succeeds("run", "--max-workers=100", "--parallel")
    }

    @Issue("https://github.com/gradle/gradle/issues/31942")
    def "execOperations.#method uses project dir as working dir by default"() {
        settingsFile << "include 'a'"
        file("a/build.gradle") << """
            def execOperations = services.get(ExecOperations)
            tasks.register("run") {
                doLast {
                    execOperations.${method} {
                        $configuration
                    }
                }
            }
        """

        when:
        succeeds("run")

        then:
        outputContains("user.dir=${testDirectory.file("a").absolutePath}")

        where:
        method     | configuration
        "exec"     | execSpecWithJavaExecutable()
        "javaexec" | javaExecSpec()
    }

    @Issue("https://github.com/gradle/gradle/issues/31942")
    def "#task task uses project dir as working dir by default"() {
        settingsFile << "include 'a'"
        file("a/build.gradle") << """
            def execOperations = services.get(ExecOperations)
            tasks.register("run", $task) {
                $configuration
            }
        """

        when:
        succeeds("run")

        then:
        outputContains("user.dir=${testDirectory.file("a").absolutePath}")

        where:
        task       | configuration
        "Exec"     | execSpecWithJavaExecutable()
        "JavaExec" | javaExecSpec()
    }

    def "produces useful help message when working directory does not exist"() {
        buildFile << """
            task run(type: Exec) {
                ${execSpecWithJavaExecutable()}
                workingDir = file("does/not/exist")
            }
        """

        when:
        fails("run")

        then:
        failure.assertHasDescription("Execution failed for task ':run' (registered in build file 'build.gradle').")
            .assertHasCause("Working directory '${file("does/not/exist")}' does not exist.")
            .assertHasNoCause("No such file or directory")
    }

    def "produces useful help message when working directory is not a directory"() {
        file("is/not/dir").touch()
        buildFile << """
            task run(type: Exec) {
                ${execSpecWithJavaExecutable()}
                workingDir = file("is/not/dir")
            }
        """

        when:
        fails("run")

        then:
        failure.assertHasDescription("Execution failed for task ':run' (registered in build file 'build.gradle').")
            .assertHasCause("Working directory '${file("is/not/dir")}' is not a directory.")
    }

    @Issue("https://github.com/gradle/gradle/issues/32213")
    def "execOperations.#method process is stopped when build is cancelled"() {
        settingsFile << "include 'a'"
        file("a/build.gradle") << """
            tasks.register("appStart") {
                def execOperations = services.get(ExecOperations)
                doLast {
                    // Using a new Thread is important to escape the task lifecycle and reproduce the issue
                    Thread.start {
                        execOperations.${method} {
                            ${configuration(getHttpServerInfoFile())}
                        }
                    }.join()
                }
            }
        """

        when:
        executer
            .requireDaemon()
            .requireIsolatedDaemons()
            .withStackTraceChecksDisabled()
        // Needed to get client pid
            .withArgument("--debug")
            .withTasks("appStart")
        def client = new DaemonClientFixture(executer.start())

        then:
        long port = waitForHttpServerPort()
        callGet("http://127.0.0.1:$port/test").statusLine.statusCode == 200

        when:
        client.kill()
        callGet("http://127.0.0.1:$port/test")

        then:
        def e = thrown(ConnectException)
        e.message.contains("Connection refused")

        where:
        method     | configuration
        "exec"     | { File serverInfoFile -> execSpecWithHttpServerExecutable(serverInfoFile) }
        "javaexec" | { File serverInfoFile -> javaExecSpecWithHttpServer(serverInfoFile) }
    }

    private static def execSpecWithJavaExecutable(def owner = "") {
        """
            ${prop(owner, "executable")}(org.gradle.internal.jvm.Jvm.current().getJavaExecutable())
            ${prop(owner, "args")}('-cp',${javaExecClasspath()}, '${TestJavaMain.name}', "Hello")
        """
    }

    private static def execSpecWithHttpServerExecutable(File serverInfoFile, def owner = "") {
        """
            ${prop(owner, "executable")}(org.gradle.internal.jvm.Jvm.current().getJavaExecutable())
            ${prop(owner, "args")}('-cp',${javaExecHttpServerClasspath()}, '${TestExecHttpServer.name}', '${TextUtil.normaliseFileSeparators(serverInfoFile.absolutePath)}')
        """
    }

    private static def javaExecSpec(def owner = "") {
        """
            ${prop(owner, "getMainClass()")}.set("${TestJavaMain.name}");
            ${prop(owner, "classpath")}(${javaExecClasspath()});
            ${prop(owner, "args")}("Hello");
        """
    }

    private static def javaExecSpecWithHttpServer(File serverInfo, def owner = "") {
        """
            ${prop(owner, "getMainClass()")}.set("${TestExecHttpServer.name}");
            ${prop(owner, "classpath")}(${javaExecHttpServerClasspath()});
            ${prop(owner, "args")}("${TextUtil.normaliseFileSeparators(serverInfo.absolutePath)}");
        """
    }

    private static String prop(String owner, String propertyName) {
        return owner ? owner + '.' + propertyName : propertyName
    }

    private static def javaExecClasspath() {
        """ "${TextUtil.escapeString(TestJavaMain.classLocation)}" """.trim()
    }

    private static def javaExecHttpServerClasspath() {
        """ "${TextUtil.escapeString(TestExecHttpServer.classLocation)}" """.trim()
    }

    private long waitForHttpServerPort(int waitTimeSeconds = 20) {
        // Server needs some time to start so we wait for the server info file with port to be created
        File serverInfoFile = getHttpServerInfoFile()
        long start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < TimeUnit.SECONDS.toMillis(waitTimeSeconds)) {
            if (serverInfoFile.exists()) {
                try {
                    return Long.parseLong(serverInfoFile.text)
                } catch (Exception ignore) {
                }
            }
            Thread.sleep(25)
        }
        throw new IllegalStateException("Cannot get server port. Was the server started?")
    }

    private File getHttpServerInfoFile() {
        return testDirectory.file("httpServerInfo")
    }

    private static HttpResponse callGet(String url) {
        try (CloseableHttpClient client = HttpClientBuilder.create().build()) {
            CloseableHttpResponse response = client.execute(new HttpGet(url))
            response.close()
            return response
        }
    }
}
