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

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.TestResources
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache
import org.gradle.internal.os.OperatingSystem
import org.gradle.process.TestJavaMain
import org.gradle.test.fixtures.dsl.GradleDsl
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.util.internal.TextUtil
import org.junit.Rule
import spock.lang.Issue

class ExecIntegrationTest extends AbstractIntegrationSpec {
    @Rule
    public final TestResources testResources = new TestResources(testDirectoryProvider)

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
        if (task == 'javaexecProjectMethod') {
            expectExecMethodDeprecation("The Project.javaexec(Closure) method", "ExecOperations.javaexec(Action) or ProviderFactory.javaexec(Action)")
            expectTaskProjectDeprecation()
        }
        succeeds task

        where:
        task << ['javaexecTask', 'javaexecProjectMethod', 'javaexecInjectedTaskAction']
    }

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
        if (task == 'execProjectMethod') {
            expectExecMethodDeprecation("The Project.exec(Closure) method", "ExecOperations.exec(Action) or ProviderFactory.exec(Action)")
            expectTaskProjectDeprecation()
        }
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
        if (task == 'execProjectMethod') {
            expectExecMethodDeprecation("The Project.exec(Closure) method", "ExecOperations.exec(Action) or ProviderFactory.exec(Action)")
            expectTaskProjectDeprecation()
        } else if (task == 'javaexecProjectMethod') {
            expectExecMethodDeprecation("The Project.javaexec(Closure) method", "ExecOperations.javaexec(Action) or ProviderFactory.javaexec(Action)")
            expectTaskProjectDeprecation()
        }
        succeeds task

        where:
        task << [
            'execTask', 'execProjectMethod', 'execInjectedTaskAction',
            'javaexecTask', 'javaexecProjectMethod', 'javaexecInjectedTaskAction'
        ]
    }

    @UnsupportedWithConfigurationCache(because = "Uses script or project at execution time")
    def "#method is deprecated in Groovy at execution time"() {
        buildFile """
            tasks.register("run") {
                doLast {
                    $method {
                        $args
                    }
                }
            }
        """

        when:
        expectExecMethodDeprecation(expectedDeprecatedMethod, replacements)
        if (method.startsWith("project.")) {
            expectTaskProjectDeprecation()
        }
        succeeds("run")

        then:
        outputContains("Hello")

        where:
        method             | args           | expectedDeprecatedMethod               | replacements
        "project.exec"     | execSpec()     | "The Project.exec(Closure) method"     | "ExecOperations.exec(Action) or ProviderFactory.exec(Action)"
        "exec"             | execSpec()     | "Using method exec(Closure)"           | "ExecOperations.exec(Action) or ProviderFactory.exec(Action)"
        "project.javaexec" | javaExecSpec() | "The Project.javaexec(Closure) method" | "ExecOperations.javaexec(Action) or ProviderFactory.javaexec(Action)"
        "javaexec"         | javaExecSpec() | "Using method javaexec(Closure)"       | "ExecOperations.javaexec(Action) or ProviderFactory.javaexec(Action)"
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

    @UnsupportedWithConfigurationCache(because = "Runs external process at configuration time")
    def "#method in #location is deprecated in Groovy at configuration time"() {
        def initScript = groovyFile("init.gradle", "")

        groovyFile(location, """
            $method {
                $args
            }
        """)

        buildFile """
            tasks.register("run") {}
        """

        when:
        expectExecMethodDeprecation(expectedDeprecatedMethod, replacements)
        succeeds("run", "-I${initScript.path}")

        then:
        outputContains("Hello")

        where:
        location          | method     | args           | expectedDeprecatedMethod         | replacements
        "build.gradle"    | "exec"     | execSpec()     | "Using method exec(Closure)"     | "ExecOperations.exec(Action) or ProviderFactory.exec(Action)"
        "build.gradle"    | "javaexec" | javaExecSpec() | "Using method javaexec(Closure)" | "ExecOperations.javaexec(Action) or ProviderFactory.javaexec(Action)"
        "settings.gradle" | "exec"     | execSpec()     | "Using method exec(Closure)"     | "ExecOperations.exec(Action) or ProviderFactory.exec(Action)"
        "settings.gradle" | "javaexec" | javaExecSpec() | "Using method javaexec(Closure)" | "ExecOperations.javaexec(Action) or ProviderFactory.javaexec(Action)"
        "init.gradle"     | "exec"     | execSpec()     | "Using method exec(Closure)"     | "ExecOperations.exec(Action) or ProviderFactory.exec(Action)"
        "init.gradle"     | "javaexec" | javaExecSpec() | "Using method javaexec(Closure)" | "ExecOperations.javaexec(Action) or ProviderFactory.javaexec(Action)"
    }

    @UnsupportedWithConfigurationCache(because = "Runs external process at configuration time")
    def "#method in precompiled #location plugin is deprecated in Groovy at configuration time"() {
        createDir("included") {
            groovyFile(file("build.gradle"), """
                plugins {
                    id("groovy-gradle-plugin")
                }
            """)
            file("src/main/groovy/my.build.gradle").touch()
            file("src/main/groovy/my.settings.gradle").touch()

            file("src/main/groovy/my.${location}.gradle") << """
                $method {
                    $args
                }
            """
        }

        file("settings.gradle") << """
            pluginManagement {
                includeBuild("included")
            }

            plugins {
                id("my")
            }
        """

        buildFile """
            plugins {
                id("my.build")
            }
            tasks.register("run") {}
        """

        when:
        expectExecMethodDeprecation(expectedDeprecatedMethod, replacements)
        succeeds("run")

        then:
        outputContains("Hello")

        where:
        location   | method     | args           | expectedDeprecatedMethod        | replacements
        "build"    | "exec"     | execSpec()     | "Using method exec(Closure)"     | "ExecOperations.exec(Action) or ProviderFactory.exec(Action)"
        "build"    | "javaexec" | javaExecSpec() | "Using method javaexec(Closure)" | "ExecOperations.javaexec(Action) or ProviderFactory.javaexec(Action)"
        "settings" | "exec"     | execSpec()     | "Using method exec(Closure)"     | "ExecOperations.exec(Action) or ProviderFactory.exec(Action)"
        "settings" | "javaexec" | javaExecSpec() | "Using method javaexec(Closure)" | "ExecOperations.javaexec(Action) or ProviderFactory.javaexec(Action)"
    }

    @UnsupportedWithConfigurationCache(because = "Uses script or project at execution time")
    def "#method is deprecated in Kotlin at execution time"() {
        buildKotlinFile << """
            tasks.register("run") {
                doLast {
                    $method {
                        $args
                    }
                }
            }
        """

        when:
        expectExecMethodDeprecation(expectedDeprecatedMethod, replacements)
        if (method.startsWith("project.")) {
            expectTaskProjectDeprecation()
        }
        succeeds("run")

        then:
        outputContains("Hello")

        where:
        method             | args           | expectedDeprecatedMethod              | replacements
        "project.exec"     | execSpec()     | "The Project.exec(Action) method"     | "ExecOperations.exec(Action) or ProviderFactory.exec(Action)"
        "exec"             | execSpec()     | "Using method exec(Action)"           | "ExecOperations.exec(Action) or ProviderFactory.exec(Action)"
        "project.javaexec" | javaExecSpec() | "The Project.javaexec(Action) method" | "ExecOperations.javaexec(Action) or ProviderFactory.javaexec(Action)"
        "javaexec"         | javaExecSpec() | "Using method javaexec(Action)"       | "ExecOperations.javaexec(Action) or ProviderFactory.javaexec(Action)"
    }

    @UnsupportedWithConfigurationCache(because = "Runs external process at configuration time")
    def "#method in #location is deprecated in Kotlin at configuration time"() {
        def initScript = file("init.gradle.kts").touch()

        file(location) << """
            $method {
                $args
            }
        """

        buildKotlinFile << """
            tasks.register("run") {}
        """

        when:
        expectExecMethodDeprecation(expectedDeprecatedMethod, replacements)
        succeeds("run", "-I${initScript.path}")

        then:
        outputContains("Hello")

        where:
        location              | method     | args           | expectedDeprecatedMethod        | replacements
        "build.gradle.kts"    | "exec"     | execSpec()     | "Using method exec(Action)"     | "ExecOperations.exec(Action) or ProviderFactory.exec(Action)"
        "build.gradle.kts"    | "javaexec" | javaExecSpec() | "Using method javaexec(Action)" | "ExecOperations.javaexec(Action) or ProviderFactory.javaexec(Action)"
        "settings.gradle.kts" | "exec"     | execSpec()     | "Using method exec(Action)"     | "ExecOperations.exec(Action) or ProviderFactory.exec(Action)"
        "settings.gradle.kts" | "javaexec" | javaExecSpec() | "Using method javaexec(Action)" | "ExecOperations.javaexec(Action) or ProviderFactory.javaexec(Action)"
        "init.gradle.kts"     | "exec"     | execSpec()     | "Using method exec(Action)"     | "ExecOperations.exec(Action) or ProviderFactory.exec(Action)"
        "init.gradle.kts"     | "javaexec" | javaExecSpec() | "Using method javaexec(Action)" | "ExecOperations.javaexec(Action) or ProviderFactory.javaexec(Action)"
    }

    @UnsupportedWithConfigurationCache(because = "Runs external process at configuration time")
    def "#method in precompiled #location plugin is deprecated in Kotlin at configuration time"() {
        createDir("included") {
            file("build.gradle.kts") << """
                plugins {
                    `kotlin-dsl`
                }

                repositories {
                    ${mavenCentralRepository(GradleDsl.KOTLIN)}
                }
            """
            file("src/main/kotlin/my.build.gradle.kts").touch()
            file("src/main/kotlin/my.settings.gradle.kts").touch()

            file("src/main/kotlin/my.${location}.gradle.kts") << """
                $method {
                    $args
                }
            """
        }

        file("settings.gradle.kts") << """
            pluginManagement {
                includeBuild("included")
            }

            plugins {
                id("my")
            }
        """

        buildKotlinFile << """
            plugins {
                id("my.build")
            }
            tasks.register("run") {}
        """
        when:
        expectExecMethodDeprecation(expectedDeprecatedMethod, replacements)
        succeeds("run")

        then:
        outputContains("Hello")

        where:
        location   | method     | args           | expectedDeprecatedMethod        | replacements
        "build"    | "exec"     | execSpec()     | "Using method exec(Action)"     | "ExecOperations.exec(Action) or ProviderFactory.exec(Action)"
        "build"    | "javaexec" | javaExecSpec() | "Using method javaexec(Action)" | "ExecOperations.javaexec(Action) or ProviderFactory.javaexec(Action)"
        "settings" | "exec"     | execSpec()     | "Using method exec(Action)"     | "ExecOperations.exec(Action) or ProviderFactory.exec(Action)"
        "settings" | "javaexec" | javaExecSpec() | "Using method javaexec(Action)" | "ExecOperations.javaexec(Action) or ProviderFactory.javaexec(Action)"
    }

    @UnsupportedWithConfigurationCache(because = "Uses script or project at execution time")
    def "project.#method is deprecated in Java at execution time"() {
        createDir("buildSrc") {
            javaFile(file("src/main/java/MyPlugin.java"), """
                import ${Plugin.name};
                import ${Project.name};

                public abstract class MyPlugin implements Plugin<Project> {
                    @Override public void apply(Project project) {
                        project.getTasks().register("run", task -> {
                           task.doLast(t -> {
                              t.getProject().$method(spec -> {
                                  $args
                              });
                           });
                        });
                    }
                }
            """)
        }

        buildFile """
            apply plugin: MyPlugin
        """

        when:
        expectExecMethodDeprecation(expectedDeprecatedMethod, replacements)
        expectTaskProjectDeprecation()
        succeeds("run")

        then:
        outputContains("Hello")

        where:
        method     | args                 | expectedDeprecatedMethod              | replacements
        "exec"     | execSpec("spec")     | "The Project.exec(Action) method"     | "ExecOperations.exec(Action) or ProviderFactory.exec(Action)"
        "javaexec" | javaExecSpec("spec") | "The Project.javaexec(Action) method" | "ExecOperations.javaexec(Action) or ProviderFactory.javaexec(Action)"
    }

    @UnsupportedWithConfigurationCache(because = "Runs external process at configuration time")
    def "project.#method is deprecated in Java at configuration time"() {
        createDir("buildSrc") {
            javaFile(file("src/main/java/MyPlugin.java"), """
                import ${Plugin.name};
                import ${Project.name};

                public abstract class MyPlugin implements Plugin<Project> {
                    @Override public void apply(Project project) {
                        project.$method(spec -> {
                            $args
                        });
                    }
                }
            """)
        }

        buildFile """
            apply plugin: MyPlugin

            tasks.register("run") {}
        """

        when:
        expectExecMethodDeprecation(expectedDeprecatedMethod, replacements)
        succeeds("run")

        then:
        outputContains("Hello")

        where:
        method     | args                 | expectedDeprecatedMethod              | replacements
        "exec"     | execSpec("spec")     | "The Project.exec(Action) method"     | "ExecOperations.exec(Action) or ProviderFactory.exec(Action)"
        "javaexec" | javaExecSpec("spec") | "The Project.javaexec(Action) method" | "ExecOperations.javaexec(Action) or ProviderFactory.javaexec(Action)"
    }

    private static def execSpec(def owner = "") {
        "${prop(owner, "commandLine")}(${echoCommandLineArgs("Hello")});"
    }

    private static def echoCommandLineArgs(String message) {
        if (OperatingSystem.current().isWindows()) {
            return """ "cmd.exe", "/d", "/c", "echo $message" """.trim()
        }
        return """ "echo", "$message" """.trim()
    }

    private static def javaExecSpec(def owner = "") {
        """
            ${prop(owner, "getMainClass()")}.set("${TestJavaMain.name}");
            ${prop(owner, "classpath")}(${javaExecClasspath()});
            ${prop(owner, "args")}("Hello");
        """
    }

    private static String prop(String owner, String propertyName) {
        return owner ? owner + '.' + propertyName : propertyName
    }

    private static def javaExecClasspath() {
        """ "${TextUtil.escapeString(TestJavaMain.classLocation)}" """.trim()
    }

    private void expectExecMethodDeprecation(String deprecation, String replacements) {
        executer.expectDocumentedDeprecationWarning("$deprecation has been deprecated. " +
            "This is scheduled to be removed in Gradle 9.0. " +
            "Use $replacements instead. " +
            "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#deprecated_project_exec")
    }

    private void expectTaskProjectDeprecation() {
        executer.expectDocumentedDeprecationWarning("Invocation of Task.project at execution time has been deprecated. "+
            "This will fail with an error in Gradle 9.0. " +
            "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_7.html#task_project")
    }
}
