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
package org.gradle.launcher

import org.gradle.api.internal.Factory
import org.gradle.api.internal.project.ServiceRegistry
import org.gradle.initialization.CommandLineConverter
import org.gradle.launcher.CommandLineActionFactory.ActionAdapter
import org.gradle.launcher.CommandLineActionFactory.ShowGuiAction
import org.gradle.launcher.CommandLineActionFactory.WithLoggingAction
import org.gradle.logging.LoggingConfiguration
import org.gradle.logging.LoggingManagerInternal
import org.gradle.util.GradleVersion
import org.gradle.util.RedirectStdOutAndErr
import org.gradle.util.SetSystemProperties
import org.junit.Rule
import spock.lang.Specification
import org.gradle.*
import org.gradle.initialization.GradleLauncherFactory

class CommandLineActionFactoryTest extends Specification {
    @Rule
    public final RedirectStdOutAndErr outputs = new RedirectStdOutAndErr();
    @Rule
    public final SetSystemProperties sysProperties = new SetSystemProperties();
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
        _ * loggingServices.get(CommandLineConverter) >> loggingConfigurationConverter
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
        outputs.stdOut.contains(new GradleVersion().prettyPrint())

        where:
        option << ['-v', '--version']
    }

    def launchesGUI() {
        when:
        def action = factory.convert(['--gui'])

        then:
        action instanceof WithLoggingAction
        action.action instanceof ActionAdapter
        action.action.action instanceof ShowGuiAction
    }

    def executesBuild() {
        when:
        def action = factory.convert(['args'])

        then:
        action instanceof WithLoggingAction
        action.action instanceof RunBuildAction
    }

    def executesBuildUsingDaemon() {
        when:
        def action = factory.convert(['--daemon', 'args'])

        then:
        action instanceof WithLoggingAction
        action.action instanceof DaemonBuildAction
    }

    def executesBuildUsingDaemonWhenSystemPropertyIsSetToTrue() {
        when:
        System.properties['org.gradle.daemon'] = 'false'
        def action = factory.convert(['args'])

        then:
        action instanceof WithLoggingAction
        action.action instanceof RunBuildAction

        when:
        System.properties['org.gradle.daemon'] = 'true'
        action = factory.convert(['args'])

        then:
        action instanceof WithLoggingAction
        action.action instanceof DaemonBuildAction
    }

    def doesNotUseDaemonWhenNoDaemonOptionPresent() {
        when:
        def action = factory.convert(['--no-daemon', 'args'])

        then:
        action instanceof WithLoggingAction
        action.action instanceof RunBuildAction
    }

    def daemonOptionTakesPrecedenceOverSystemProperty() {
        when:
        System.properties['org.gradle.daemon'] = 'false'
        def action = factory.convert(['--daemon', 'args'])

        then:
        action instanceof WithLoggingAction
        action.action instanceof DaemonBuildAction

        when:
        System.properties['org.gradle.daemon'] = 'true'
        action = factory.convert(['--no-daemon', 'args'])

        then:
        action instanceof WithLoggingAction
        action.action instanceof RunBuildAction
    }
    
    def stopsDaemon() {
        when:
        def action = factory.convert(['--stop'])

        then:
        action instanceof WithLoggingAction
        action.action instanceof StopDaemonAction
    }

    def runsDaemonInForeground() {
        when:
        def action = factory.convert(['--foreground'])

        then:
        action instanceof WithLoggingAction
        action.action instanceof ActionAdapter
        action.action.action instanceof DaemonMain
    }
}
