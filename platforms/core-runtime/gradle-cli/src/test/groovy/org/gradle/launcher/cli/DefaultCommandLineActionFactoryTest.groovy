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
package org.gradle.launcher.cli

import org.apache.tools.ant.Main
import org.gradle.api.Action
import org.gradle.cli.CommandLineArgumentException
import org.gradle.cli.CommandLineParser
import org.gradle.internal.Factory
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.logging.LoggingManagerInternal
import org.gradle.internal.logging.events.OutputEventListener
import org.gradle.internal.logging.progress.ProgressLoggerFactory
import org.gradle.internal.logging.text.StreamingStyledTextOutput
import org.gradle.internal.logging.text.StyledTextOutput
import org.gradle.internal.logging.text.StyledTextOutputFactory
import org.gradle.internal.os.OperatingSystem
import org.gradle.internal.service.ServiceRegistry
import org.gradle.launcher.bootstrap.CommandLineActionFactory
import org.gradle.launcher.bootstrap.ExecutionListener
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.SetSystemProperties
import org.gradle.util.internal.DefaultGradleVersion
import org.gradle.util.internal.RedirectStdOutAndErr
import org.junit.Rule
import spock.lang.Specification

class DefaultCommandLineActionFactoryTest extends Specification {
    @Rule
    public final RedirectStdOutAndErr outputs = new RedirectStdOutAndErr();
    @Rule
    public final SetSystemProperties sysProperties = new SetSystemProperties();
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass());
    final ExecutionListener executionListener = Mock()
    final ServiceRegistry loggingServices = Mock()
    final ServiceRegistry basicServices = Mock()
    final LoggingManagerInternal loggingManager = Mock()
    final CommandLineActionCreator actionFactory1 = Mock()
    final CommandLineActionCreator actionFactory2 = Mock()
    final CommandLineActionFactory factory = new DefaultCommandLineActionFactory() {
        @Override
        protected ServiceRegistry createLoggingServices() {
            return loggingServices
        }

        @Override
        ServiceRegistry createBasicGlobalServices(ServiceRegistry loggingServices) {
            return basicServices
        }

        @Override
        protected void createBuildActionFactoryActionCreator(ServiceRegistry loggingServices, ServiceRegistry basicServices, List<CommandLineActionCreator> actionCreators) {
            actionCreators.add(actionFactory1)
            actionCreators.add(actionFactory2)
        }
    }

    def setup() {
        ProgressLoggerFactory progressLoggerFactory = Mock()
        _ * loggingServices.get(ProgressLoggerFactory) >> progressLoggerFactory
        _ * loggingServices.get(OutputEventListener) >> Mock(OutputEventListener)
        Factory<LoggingManagerInternal> loggingManagerFactory = Mock()
        _ * loggingServices.getFactory(LoggingManagerInternal) >> loggingManagerFactory
        _ * loggingManagerFactory.create() >> loggingManager
        StyledTextOutputFactory textOutputFactory = Mock()
        _ * loggingServices.get(StyledTextOutputFactory) >> textOutputFactory
        StyledTextOutput textOutput = new StreamingStyledTextOutput(outputs.stdErrPrintStream)
        _ * textOutputFactory.create(_, _) >> textOutput

        tmpDir.file("settings.gradle").touch() // To prevent layout from detecting files from gradle/gradle build
    }

    def "delegates to each action factory to configure the command-line parser and create the action"() {
        def rawAction = Mock(Action<? super ExecutionListener>)

        when:
        def commandLineExecution = factory.convert(["--some-option"])
        commandLineExecution.execute(executionListener)

        then:
        1 * actionFactory1.configureCommandLineParser(!null) >> {
            CommandLineParser parser -> parser.option("some-option")
        }
        1 * actionFactory2.configureCommandLineParser(!null)
        1 * actionFactory1.createAction(!null, !null, !null) >> rawAction
        1 * rawAction.execute(executionListener)
    }

    def "configures logging before parsing command-line"() {
        Action<ExecutionListener> rawAction = Mock()

        when:
        def commandLineExecution = factory.convert([])
        commandLineExecution.execute(executionListener)

        then:
        1 * loggingManager.start()

        and:
        1 * actionFactory1.configureCommandLineParser(!null)
        1 * actionFactory2.configureCommandLineParser(!null)
        1 * actionFactory1.createAction(!null, !null, !null) >> rawAction
    }

    def "reports command-line parse failure"() {
        when:
        def commandLineExecution = factory.convert(options.split().toList())
        commandLineExecution.execute(executionListener)

        then:
        outputs.stdErr.contains(expectedMessage)
        outputs.stdErr.contains('USAGE: gradle [option...] [task...]')
        outputs.stdErr.contains('--help')
        !outputs.stdErr.contains('To see help contextual to the project, use gradle help')
        outputs.stdErr.contains('To see more detail about a task, run gradle help --task <task>')
        if (expectedSuggestion != null) {
            assert outputs.stdErr.contains("For example, gradle help --task $expectedSuggestion")
        } else {
            assert !outputs.stdErr.contains("For example, gradle help --task ")
        }
        outputs.stdErr.contains('To see a list of available tasks, run gradle tasks')
        outputs.stdErr.contains('--some-option')

        and:
        1 * actionFactory1.configureCommandLineParser(!null) >> {CommandLineParser parser -> parser.option('some-option')}
        1 * executionListener.onFailure({it instanceof CommandLineArgumentException})
        0 * executionListener._

        where:
        options                   | expectedMessage                                             | expectedSuggestion
        '--broken'                | "Unknown command-line option '--broken'"                    | null
        'wrapper --version=1.2.3' | "Command-line option '--version' does not take an argument" | 'wrapper'
        '--version=1.2.3'         | "Command-line option '--version' does not take an argument" | null
        '--wrapper'               | "Unknown command-line option '--wrapper'"                   | null
    }

    def "reports failure to build action due to command-line parse failure"() {
        def failure = new CommandLineArgumentException("<broken>")

        when:
        def commandLineExecution = factory.convert(['--some-option'])
        commandLineExecution.execute(executionListener)

        then:
        outputs.stdErr.contains('<broken>')
        outputs.stdErr.contains('USAGE: gradle [option...] [task...]')
        outputs.stdErr.contains('--help')
        outputs.stdErr.contains('--some-option')

        and:
        1 * actionFactory1.configureCommandLineParser(!null) >> {CommandLineParser parser -> parser.option('some-option')}
        1 * actionFactory1.createAction(!null, !null, !null) >> { throw failure }
        1 * executionListener.onFailure(failure)
        0 * executionListener._
    }

    def "continues on failure to parse logging configuration"() {
        when:
        def commandLineExecution = factory.convert(["--logging=broken"])
        commandLineExecution.execute(executionListener)

        then:
        outputs.stdErr.contains('--logging')
        outputs.stdErr.contains('USAGE: gradle [option...] [task...]')
        outputs.stdErr.contains('--help')
        outputs.stdErr.contains('--some-option')

        and:
        1 * actionFactory1.configureCommandLineParser(!null) >> {CommandLineParser parser -> parser.option('some-option')}
        1 * executionListener.onFailure({it instanceof CommandLineArgumentException})
        0 * executionListener._
    }

    def "reports other failure to build action"() {
        def failure = new RuntimeException("<broken>")

        when:
        def commandLineExecution = factory.convert([])
        commandLineExecution.execute(executionListener)

        then:
        outputs.stdErr.contains('<broken>')

        and:
        1 * actionFactory1.createAction(!null, !null, !null) >> { throw failure }
        1 * executionListener.onFailure(failure)
        0 * executionListener._
    }

    def "displays usage message"() {
        when:
        def commandLineExecution = factory.convert([option])
        commandLineExecution.execute(executionListener)

        then:
        outputs.stdOut.contains('To see help contextual to the project, use gradle help')
        outputs.stdOut.contains('To see more detail about a task, run gradle help --task <task>')
        !outputs.stdOut.contains("For example, gradle help --task")
        outputs.stdOut.contains('To see a list of available tasks, run gradle tasks')
        outputs.stdOut.contains('USAGE: gradle [option...] [task...]')
        outputs.stdOut.contains('--help')
        outputs.stdOut.contains('--some-option')

        and:
        1 * actionFactory1.configureCommandLineParser(!null) >> {CommandLineParser parser -> parser.option('some-option')}
        0 * executionListener._

        where:
        option << ['-h', '-?', '--help']
    }

    def "displays example for task "() {
        when:
        def commandLineExecution = factory.convert(options.split().toList())
        commandLineExecution.execute(executionListener)

        then:
        outputs.stdOut.contains('To see help contextual to the project, use gradle help')
        outputs.stdOut.contains('To see more detail about a task, run gradle help --task <task>')
        outputs.stdOut.contains("For example, gradle help --task init")
        outputs.stdOut.contains('To see a list of available tasks, run gradle tasks')
        outputs.stdOut.contains('USAGE: gradle [option...] [task...]')
        outputs.stdOut.contains('--help')
        outputs.stdOut.contains('--some-option')

        and:
        1 * actionFactory1.configureCommandLineParser(!null) >> { CommandLineParser parser -> parser.option('some-option') }
        0 * executionListener._

        where:
        options << ['-h init', 'init -?', 'init --help', '--help init']
    }

    def "uses system property for application name"() {
        System.setProperty("org.gradle.appname", "gradle-app");

        when:
        def commandLineExecution = factory.convert(['-?'])
        commandLineExecution.execute(executionListener)

        then:
        outputs.stdOut.contains('To see help contextual to the project, use gradle-app help')
        outputs.stdOut.contains('USAGE: gradle-app [option...] [task...]')
    }

    private static final String EXPECTED_VERSION_TEXT
    static {
        def version = DefaultGradleVersion.current()
        EXPECTED_VERSION_TEXT = [
            "",
            "------------------------------------------------------------",
            "Gradle ${version.version}",
            "------------------------------------------------------------",
            "",
            "Build time:    $version.buildTimestamp",
            "Revision:      $version.gitRevision",
            "",
            "Kotlin:        ${KotlinDslVersion.current().kotlinVersion}",
            "Groovy:        $GroovySystem.version",
            "Ant:           $Main.antVersion",
            "Launcher JVM:  ${Jvm.current()}",
            "Daemon JVM:    ${Jvm.current().javaHome.absolutePath} (no JDK specified, using current Java home)",
            "OS:            ${OperatingSystem.current()}",
            ""
        ].join(System.lineSeparator())
    }

    def "displays version message"() {
        when:
        def commandLineExecution = factory.convert(options + ["-p${tmpDir.getTestDirectory()}".toString()])
        commandLineExecution.execute(executionListener)

        then:
        outputs.stdOut.contains(EXPECTED_VERSION_TEXT)

        and:
        1 * actionFactory1.configureCommandLineParser(!null) >> {CommandLineParser parser -> parser.option('some-option')}
        0 * actionFactory1.createAction(_, _)
        1 * loggingManager.start()
        0 * executionListener._

        where:
        options << [['-v', '--version'], ['', '--some-option']].combinations().collect { it.findAll() }
    }

    def "displays version message and continues build"() {
        when:
        def commandLineExecution = factory.convert(options + ["-p${tmpDir.getTestDirectory()}".toString()])
        commandLineExecution.execute(executionListener)

        then:
        outputs.stdOut.contains(EXPECTED_VERSION_TEXT)
        outputs.stdOut.contains("action1")
        !action1Intermediary || outputs.stdOut.contains("action2")

        and:
        1 * actionFactory1.createAction(!null, !null, !null) >> {
            def action1 = { println "action1" }
            action1Intermediary ? action1 as ContinuingAction<ExecutionListener> : action1 as Action<ExecutionListener>

        }
        action2Called * actionFactory2.createAction(!null, !null, !null) >> {
            { println "action2" } as Action<ExecutionListener>
        }
        1 * loggingManager.start()
        0 * executionListener._

        where:
        options            | action1Intermediary | action2Called
        ['-V']             | false               | 0
        ['--show-version'] | false               | 0
        ['-V']             | true                | 1
        ['--show-version'] | true                | 1
    }
}
