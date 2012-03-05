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

import org.gradle.BuildResult
import org.gradle.GradleLauncher
import org.gradle.StartParameter
import org.gradle.cli.CommandLineArgumentException
import org.gradle.cli.CommandLineConverter
import org.gradle.initialization.GradleLauncherFactory
import org.gradle.internal.Factory
import org.gradle.internal.service.ServiceRegistry
import org.gradle.launcher.daemon.bootstrap.DaemonMain
import org.gradle.launcher.exec.ExceptionReportingAction
import org.gradle.launcher.exec.ExecutionListener
import org.gradle.logging.LoggingConfiguration
import org.gradle.logging.LoggingManagerInternal
import org.gradle.logging.ProgressLoggerFactory
import org.gradle.logging.internal.OutputEventListener
import org.gradle.util.GradleVersion
import org.gradle.util.RedirectStdOutAndErr
import org.gradle.util.SetSystemProperties
import org.gradle.util.TemporaryFolder
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.launcher.cli.CommandLineActionFactory.*
import org.gradle.internal.os.OperatingSystem
import org.gradle.launcher.daemon.configuration.DaemonParameters

class CommandLineActionFactoryTest extends Specification {
    @Rule
    public final RedirectStdOutAndErr outputs = new RedirectStdOutAndErr();
    @Rule
    public final SetSystemProperties sysProperties = new SetSystemProperties();
    @Rule
    TemporaryFolder tmpDir = new TemporaryFolder();
    final ExecutionListener buildCompleter = Mock()
    final CommandLineConverter<StartParameter> startParameterConverter = Mock()
    final GradleLauncherFactory gradleLauncherFactory = Mock()
    final GradleLauncher gradleLauncher = Mock()
    final BuildResult buildResult = Mock()
    final ServiceRegistry loggingServices = Mock()
    final CommandLineConverter<LoggingConfiguration> loggingConfigurationConverter = Mock()
    final LoggingManagerInternal loggingManager = Mock()
    final CommandLineActionFactory factory = new CommandLineActionFactory() {
        @Override
        ServiceRegistry createLoggingServices() {
            return loggingServices
        }

        @Override
        CommandLineConverter<StartParameter> createStartParameterConverter() {
            return startParameterConverter
        }
    }

    def setup() {        
        ProgressLoggerFactory progressLoggerFactory = Mock()
        _ * loggingServices.get(ProgressLoggerFactory) >> progressLoggerFactory
        _ * loggingServices.get(CommandLineConverter) >> loggingConfigurationConverter
        _ * loggingServices.get(OutputEventListener) >> Mock(OutputEventListener)
        _ * loggingConfigurationConverter.convert(!null) >> new LoggingConfiguration()
        Factory<LoggingManagerInternal> loggingManagerFactory = Mock()
        _ * loggingServices.getFactory(LoggingManagerInternal) >> loggingManagerFactory
        _ * loggingManagerFactory.create() >> loggingManager
    }

    def reportsCommandLineParseFailure() {
        def failure = new CommandLineArgumentException('<broken>')

        when:
        def action = factory.convert([])

        then:
        1 * startParameterConverter.configure(!null) >> { args -> args[0].option('some-build-option') }
        1 * startParameterConverter.convert(!null, !null) >> { throw failure }

        when:
        action.execute(buildCompleter)

        then:
        1 * loggingManager.start()
        outputs.stdErr.contains('<broken>')
        outputs.stdErr.contains('USAGE: gradle [option...] [task...]')
        outputs.stdErr.contains('--help')
        outputs.stdErr.contains('--some-build-option')
        1 * buildCompleter.onFailure(failure)
    }

    def displaysUsageMessage() {
        when:
        def action = factory.convert([option])
        action.execute(buildCompleter)

        then:
        _ * startParameterConverter.configure(!null) >> { args -> args[0].option('some-build-option') }
        1 * loggingManager.start()
        outputs.stdOut.contains('USAGE: gradle [option...] [task...]')
        outputs.stdOut.contains('--help')
        outputs.stdOut.contains('--some-build-option')

        where:
        option << ['-h', '-?', '--help']
    }

    def usesSystemPropertyForGradleAppName() {
        System.setProperty("org.gradle.appname", "gradle-app");

        when:
        def action = factory.convert(['-?'])
        action.execute(buildCompleter)

        then:
        outputs.stdOut.contains('USAGE: gradle-app [option...] [task...]')
    }

    def displaysVersionMessage() {
        when:
        def action = factory.convert([option])
        action.execute(buildCompleter)

        then:
        1 * loggingManager.start()
        outputs.stdOut.contains(GradleVersion.current().prettyPrint())

        where:
        option << ['-v', '--version']
    }

    def launchesGUI() {
        when:
        def action = factory.convert(['--gui'])

        then:
        action instanceof WithLoggingAction
        action.action instanceof ExceptionReportingAction
        action.action.action instanceof ActionAdapter
        action.action.action.action instanceof ShowGuiAction
    }

    def executesBuild() {
        when:
        def action = factory.convert(['args'])

        then:
        isInProcess(action)
    }

    def executesBuildUsingDaemon() {
        when:
        def action = factory.convert(['--daemon', 'args'])

        then:
        isDaemon action
    }

    def executesBuildUsingDaemonWhenSystemPropertyIsSetToTrue() {
        when:
        System.properties['org.gradle.daemon'] = 'false'
        def action = factory.convert(['args'])

        then:
        isInProcess action

        when:
        System.properties['org.gradle.daemon'] = 'true'
        action = factory.convert(['args'])

        then:
        isDaemon action
    }

    def doesNotUseDaemonWhenNoDaemonOptionPresent() {
        when:
        def action = factory.convert(['--no-daemon', 'args'])

        then:
        isInProcess action
    }

    def daemonOptionTakesPrecedenceOverSystemProperty() {
        when:
        System.properties['org.gradle.daemon'] = 'false'
        def action = factory.convert(['--daemon', 'args'])

        then:
        isDaemon action

        when:
        System.properties['org.gradle.daemon'] = 'true'
        action = factory.convert(['--no-daemon', 'args'])

        then:
        isInProcess action
    }

    def stopsDaemon() {
        when:
        def action = factory.convert(['--stop'])

        then:
        action instanceof WithLoggingAction
        action.action instanceof ExceptionReportingAction
        action.action.action instanceof ActionAdapter
        action.action.action.action instanceof StopDaemonAction
    }

    def runsDaemonInForeground() {
        when:
        def action = factory.convert(['--foreground'])

        then:
        action instanceof WithLoggingAction
        action.action instanceof ExceptionReportingAction
        action.action.action instanceof ActionAdapter
        action.action.action.action instanceof DaemonMain
    }

    def executesBuildWithSingleUseDaemonIfJavaHomeIsNotCurrent() {
        when:
        def javaHome = tmpDir.createDir("javahome")
        javaHome.createFile(OperatingSystem.current().getExecutableName("bin/java"))

        System.properties['org.gradle.java.home'] = javaHome.canonicalPath
        def action = factory.convert([])

        then:
        isSingleUseDaemon action
    }

    def "daemon setting in precedence is system prop, user home then project directory"() {
        given:
        def userHome = tmpDir.createDir("user_home")
        userHome.file("gradle.properties").withOutputStream { outstr ->
            new Properties((DaemonParameters.DAEMON_SYS_PROPERTY): 'false').store(outstr, "HEADER")
        }
        def projectDir = tmpDir.createDir("project_dir")
        projectDir.createFile("settings.gradle")
        projectDir.file("gradle.properties").withOutputStream { outstr ->
            new Properties((DaemonParameters.DAEMON_SYS_PROPERTY): 'true').store(outstr, "HEADER")
        }

        when:
        def action = factory.convert([])

        then:
        startParameterConverter.convert(!null, !null) >> { args, startParam ->
            startParam.currentDir = projectDir
        }

        and:
        isDaemon action

        when:
        action = factory.convert([])

        then:
        startParameterConverter.convert(!null, !null) >> { args, startParam ->
            startParam.gradleUserHomeDir = userHome
            startParam.currentDir = projectDir
        }

        and:
        isInProcess action

        when:
        System.properties['org.gradle.daemon'] = 'true'
        action = factory.convert([])

        then:
        startParameterConverter.convert(!null, !null) >> { args, startParam ->
            startParam.gradleUserHomeDir = userHome
            startParam.currentDir = projectDir
        }

        and:
        isDaemon action
    }

    def isDaemon(def action) {
        assert action instanceof WithLoggingAction
        assert action.action instanceof ExceptionReportingAction
        assert action.action.action instanceof ActionAdapter
        action.action.action.action instanceof DaemonBuildAction
    }

    def isInProcess(def action) {
        assert action instanceof WithLoggingAction
        assert action.action instanceof ExceptionReportingAction
        action.action.action instanceof RunBuildAction
    }

    def isSingleUseDaemon(def action) {
        assert action instanceof WithLoggingAction
        assert action.action instanceof ExceptionReportingAction
        assert action.action.action instanceof ActionAdapter
        action.action.action.action instanceof DaemonBuildAction
    }
}
