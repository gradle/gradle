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
import org.gradle.launcher.exec.InProcessGradleLauncherActionExecuter
import org.gradle.logging.ProgressLoggerFactory
import org.gradle.logging.internal.OutputEventListener
import org.gradle.util.SetSystemProperties
import org.gradle.util.TemporaryFolder
import org.junit.Rule
import spock.lang.Specification

class BuildActionsFactoryTest extends Specification {
    @Rule
    public final SetSystemProperties sysProperties = new SetSystemProperties();
    @Rule
    TemporaryFolder tmpDir = new TemporaryFolder();
    final CommandLineConverter<StartParameter> startParameterConverter = Mock()
    final ServiceRegistry loggingServices = Mock()
    final BuildActionsFactory factory = new BuildActionsFactory(loggingServices, startParameterConverter)

    def setup() {        
        _ * loggingServices.get(OutputEventListener) >> Mock(OutputEventListener)
        _ * loggingServices.get(ProgressLoggerFactory) >> Mock(ProgressLoggerFactory)
    }

    def executesBuild() {
        when:
        def action = convert('args')

        then:
        isInProcess(action)
    }

    def executesBuildUsingDaemon() {
        when:
        def action = convert('--daemon', 'args')

        then:
        isDaemon action
    }

    def executesBuildUsingDaemonWhenSystemPropertyIsSetToTrue() {
        when:
        System.properties['org.gradle.daemon'] = 'false'
        def action = convert('args')

        then:
        isInProcess action

        when:
        System.properties['org.gradle.daemon'] = 'true'
        action = convert('args')

        then:
        isDaemon action
    }

    def doesNotUseDaemonWhenNoDaemonOptionPresent() {
        when:
        def action = convert('--no-daemon', 'args')

        then:
        isInProcess action
    }

    def daemonOptionTakesPrecedenceOverSystemProperty() {
        when:
        System.properties['org.gradle.daemon'] = 'false'
        def action = convert('--daemon', 'args')

        then:
        isDaemon action

        when:
        System.properties['org.gradle.daemon'] = 'true'
        action = convert('--no-daemon', 'args')

        then:
        isInProcess action
    }

    def stopsDaemon() {
        when:
        def action = convert('--stop')

        then:
        // Relying on impl of Actions.toAction(Runnable)
        action.runnable instanceof StopDaemonAction
    }

    def runsDaemonInForeground() {
        when:
        def action = convert('--foreground')

        then:
        // Relying on impl of Actions.toAction(Runnable)
        action.runnable instanceof DaemonMain
    }

    def executesBuildWithSingleUseDaemonIfJavaHomeIsNotCurrent() {
        when:
        def javaHome = tmpDir.createDir("javahome")
        javaHome.createFile(OperatingSystem.current().getExecutableName("bin/java"))

        System.properties['org.gradle.java.home'] = javaHome.canonicalPath
        def action = convert()

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
        def action = convert()

        then:
        startParameterConverter.convert(!null, !null) >> { args, startParam ->
            startParam.currentDir = projectDir
        }

        and:
        isDaemon action

        when:
        action = convert()

        then:
        startParameterConverter.convert(!null, !null) >> { args, startParam ->
            startParam.gradleUserHomeDir = userHome
            startParam.currentDir = projectDir
        }

        and:
        isInProcess action

        when:
        System.properties['org.gradle.daemon'] = 'true'
        action = convert()

        then:
        startParameterConverter.convert(!null, !null) >> { args, startParam ->
            startParam.gradleUserHomeDir = userHome
            startParam.currentDir = projectDir
        }

        and:
        isDaemon action
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
