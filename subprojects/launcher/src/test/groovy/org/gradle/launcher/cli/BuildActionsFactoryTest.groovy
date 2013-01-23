/*
 * Copyright 2012 the original author or authors.
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

import org.gradle.StartParameter
import org.gradle.cli.CommandLineConverter
import org.gradle.cli.CommandLineParser
import org.gradle.internal.os.OperatingSystem
import org.gradle.internal.service.ServiceRegistry
import org.gradle.launcher.daemon.bootstrap.DaemonMain
import org.gradle.launcher.daemon.client.DaemonClient
import org.gradle.launcher.daemon.client.SingleUseDaemonClient
import org.gradle.launcher.daemon.configuration.DaemonParameters
import org.gradle.launcher.daemon.configuration.GradlePropertiesConfigurer
import org.gradle.launcher.exec.InProcessGradleLauncherActionExecuter
import org.gradle.logging.ProgressLoggerFactory
import org.gradle.logging.internal.OutputEventListener
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.SetSystemProperties
import org.junit.Rule
import spock.lang.Specification

class BuildActionsFactoryTest extends Specification {
    @Rule
    public final SetSystemProperties sysProperties = new SetSystemProperties();
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider();
    final CommandLineConverter<StartParameter> startParameterConverter = Mock()
    final ServiceRegistry loggingServices = Mock()
    final GradlePropertiesConfigurer configurer = Stub()
    final BuildActionsFactory factory = new BuildActionsFactory(loggingServices, startParameterConverter, configurer)

    def setup() {        
        _ * loggingServices.get(OutputEventListener) >> Mock(OutputEventListener)
        _ * loggingServices.get(ProgressLoggerFactory) >> Mock(ProgressLoggerFactory)
    }

    def "executes build"() {
        configurer.configureParameters(_ as StartParameter) >> new DaemonParameters()

        when:
        def action = convert('args')

        then:
        isInProcess(action)
    }

    def "by default daemon is not used"() {
        configurer.configureParameters(_) >> new DaemonParameters().setEnabled(false)

        when:
        def action = convert('args')

        then:
        isInProcess action
    }

    def "daemon is used when command line option is used"() {
        configurer.configureParameters(_) >> new DaemonParameters().setEnabled(false)

        when:
        def action = convert('--daemon', 'args')

        then:
        isDaemon action
    }

    def "daemon is used when daemon parameters say so"() {
        configurer.configureParameters(_) >> new DaemonParameters().setEnabled(true)

        when:
        def action = convert('args')

        then:
        isDaemon action
    }

    def "does not use daemon when no-daemon command line option issued"() {
        configurer.configureParameters(_) >> new DaemonParameters().setEnabled(true)

        when:
        def action = convert('--no-daemon', 'args')

        then:
        isInProcess action
    }

    def "stops daemon"() {
        configurer.configureParameters(_) >> new DaemonParameters()

        when:
        def action = convert('--stop')

        then:
        // Relying on impl of Actions.toAction(Runnable)
        action.runnable instanceof StopDaemonAction
    }

    def "runs daemon in foreground"() {
        configurer.configureParameters(_) >> new DaemonParameters()

        when:
        def action = convert('--foreground')

        then:
        // Relying on impl of Actions.toAction(Runnable)
        action.runnable instanceof DaemonMain
    }

    def "executes with single use daemon if java home is not current"() {
        given:
        def javaHome = tmpDir.createDir("javahome")
        javaHome.createFile(OperatingSystem.current().getExecutableName("bin/java"))
        configurer.configureParameters(_) >> new DaemonParameters().setJavaHome(javaHome.canonicalFile)

        when:
        def action = convert()

        then:
        isSingleUseDaemon action
    }

    def convert(String... args) {
        def CommandLineParser parser = new CommandLineParser()
        factory.configureCommandLineParser(parser)
        def cl = parser.parse(args)
        return factory.createAction(parser, cl)
    }

    void isDaemon(def action) {
        // Relying on impl of Actions.toAction(Runnable)
        assert action.runnable instanceof RunBuildAction
        assert action.runnable.executer instanceof DaemonClient
    }

    void isInProcess(def action) {
        // Relying on impl of Actions.toAction(Runnable)
        assert action.runnable instanceof RunBuildAction
        assert action.runnable.executer instanceof InProcessGradleLauncherActionExecuter
    }

    void isSingleUseDaemon(def action) {
        // Relying on impl of Actions.toAction(Runnable)
        assert action.runnable instanceof RunBuildAction
        assert action.runnable.executer instanceof SingleUseDaemonClient
    }
}
