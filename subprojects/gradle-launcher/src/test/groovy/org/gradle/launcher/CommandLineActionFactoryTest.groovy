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

import org.gradle.initialization.CommandLineConverter
import org.gradle.util.GradleVersion
import org.gradle.util.RedirectStdOutAndErr
import org.gradle.util.SetSystemProperties
import org.junit.Rule
import spock.lang.Specification
import org.gradle.*
import org.gradle.api.internal.project.ServiceRegistry
import org.gradle.logging.LoggingConfiguration
import org.gradle.logging.LoggingManagerInternal
import org.gradle.api.internal.Factory

class CommandLineActionFactoryTest extends Specification {
    @Rule
    public final RedirectStdOutAndErr outputs = new RedirectStdOutAndErr();
    @Rule
    public final SetSystemProperties sysProperties = new SetSystemProperties();
    final BuildCompleter buildCompleter = Mock()
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

        @Override
        GradleLauncherFactory createGradleLauncherFactory(ServiceRegistry loggingServices) {
            return gradleLauncherFactory
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
        1 * startParameterConverter.convert(!null) >> { throw failure }

        when:
        action.execute(buildCompleter)

        then:
        1 * loggingManager.start()
        outputs.stdErr.contains('<broken>')
        outputs.stdErr.contains('USAGE: gradle [option...] [task...]')
        outputs.stdErr.contains('--help')
        outputs.stdErr.contains('--some-build-option')
        1 * buildCompleter.exit(failure)
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
        1 * buildCompleter.exit(null)

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
        1 * buildCompleter.exit(null)
    }

    def displaysVersionMessage() {
        when:
        def action = factory.convert([option])
        action.execute(buildCompleter)

        then:
        1 * loggingManager.start()
        outputs.stdOut.contains(new GradleVersion().prettyPrint())
        1 * buildCompleter.exit(null)

        where:
        option << ['-v', '--version']
    }

    def launchesGUI() {
        when:
        def action = factory.convert(['--gui'])

        then:
        action instanceof CommandLineActionFactory.WithLoggingAction
        action.action instanceof CommandLineActionFactory.CompleteOnSuccessAction
        action.action.action instanceof CommandLineActionFactory.ShowGuiAction
    }

    def executesBuild() {
        def startParameter = new StartParameter();

        when:
        def action = factory.convert(['args'])

        then:
        1 * startParameterConverter.convert(!null) >> startParameter

        when:
        action.execute(buildCompleter)

        then:
        1 * loggingManager.start()
        1 * gradleLauncherFactory.newInstance(startParameter) >> gradleLauncher
        1 * gradleLauncher.run() >> buildResult
        1 * buildResult.failure >> null
        1 * buildCompleter.exit(null)
    }

    def executesFailedBuild() {
        def RuntimeException failure = new RuntimeException()
        def startParameter = new StartParameter();

        when:
        def action = factory.convert(['args'])

        then:
        1 * startParameterConverter.convert(!null) >> startParameter

        when:
        action.execute(buildCompleter)

        then:
        1 * loggingManager.start()
        1 * gradleLauncherFactory.newInstance(startParameter) >> gradleLauncher
        1 * gradleLauncher.run() >> buildResult
        1 * buildResult.failure >> failure
        1 * buildCompleter.exit(failure)
    }
}
