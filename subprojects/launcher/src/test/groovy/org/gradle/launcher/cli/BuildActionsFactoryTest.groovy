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
import org.gradle.cli.CommandLineParser
import org.gradle.cli.SystemPropertiesCommandLineConverter
import org.gradle.initialization.DefaultCommandLineConverter
import org.gradle.initialization.LayoutCommandLineConverter
import org.gradle.internal.Factory
import org.gradle.internal.invocation.BuildActionRunner
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.jvm.inspection.JvmVersionDetector
import org.gradle.internal.logging.LoggingManagerInternal
import org.gradle.internal.logging.events.OutputEventListener
import org.gradle.internal.logging.progress.ProgressLoggerFactory
import org.gradle.internal.logging.text.StyledTextOutputFactory
import org.gradle.internal.nativeintegration.filesystem.FileSystem
import org.gradle.internal.service.ServiceRegistry
import org.gradle.internal.service.scopes.PluginServiceRegistry
import org.gradle.launcher.cli.converter.DaemonCommandLineConverter
import org.gradle.launcher.cli.converter.LayoutToPropertiesConverter
import org.gradle.launcher.cli.converter.PropertiesToDaemonParametersConverter
import org.gradle.launcher.cli.converter.PropertiesToStartParameterConverter
import org.gradle.launcher.daemon.bootstrap.ForegroundDaemonAction
import org.gradle.launcher.daemon.client.DaemonClient
import org.gradle.launcher.daemon.client.SingleUseDaemonClient
import org.gradle.launcher.daemon.configuration.DaemonParameters
import org.gradle.launcher.exec.InProcessBuildActionExecuter
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.tooling.internal.provider.ContinuousBuildActionExecuter
import org.gradle.tooling.internal.provider.GradleThreadBuildActionExecuter
import org.gradle.tooling.internal.provider.ServicesSetupBuildActionExecuter
import org.gradle.tooling.internal.provider.SessionFailureReportingActionExecuter
import org.gradle.tooling.internal.provider.SetupLoggingActionExecuter
import org.gradle.tooling.internal.provider.StartParamsValidatingActionExecuter
import org.gradle.util.SetSystemProperties
import org.gradle.util.UsesNativeServices
import org.junit.Rule
import spock.lang.Specification

@UsesNativeServices
class BuildActionsFactoryTest extends Specification {
    @Rule
    public final SetSystemProperties sysProperties = new SetSystemProperties();
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider();
    ServiceRegistry loggingServices = Mock()
    PropertiesToDaemonParametersConverter propertiesToDaemonParametersConverter = Stub()
    PropertiesToStartParameterConverter propertiesToStartParameterConverter = Stub()
    JvmVersionDetector jvmVersionDetector = Stub()
    ParametersConverter parametersConverter = new ParametersConverter(
            Stub(LayoutCommandLineConverter), Stub(SystemPropertiesCommandLineConverter),
            Stub(LayoutToPropertiesConverter), propertiesToStartParameterConverter,
            new DefaultCommandLineConverter(), new DaemonCommandLineConverter(),
            propertiesToDaemonParametersConverter)

    BuildActionsFactory factory = new BuildActionsFactory(loggingServices, parametersConverter, jvmVersionDetector)

    def setup() {
        _ * loggingServices.get(OutputEventListener) >> Mock(OutputEventListener)
        _ * loggingServices.get(ProgressLoggerFactory) >> Mock(ProgressLoggerFactory)
        _ * loggingServices.getAll(BuildActionRunner) >> []
        _ * loggingServices.get(StyledTextOutputFactory) >> Mock(StyledTextOutputFactory)
        _ * loggingServices.get(FileSystem) >> Mock(FileSystem)
        _ * loggingServices.getFactory(LoggingManagerInternal) >> Mock(Factory)
        _ * loggingServices.getAll(PluginServiceRegistry) >> []
        _ * loggingServices.getAll(_) >> []
    }

    def "check that --max-workers overrides org.gradle.workers.max"() {
        when:
        propertiesToStartParameterConverter.convert(_, _) >> { args ->
            def startParameter = (StartParameter) args[1]
            startParameter.setMaxWorkerCount(3)
        }
        RunBuildAction action = convert('--max-workers=5')

        then:
        action.startParameter.maxWorkerCount == 5
    }

    def "by default daemon is used"() {
        when:
        def action = convert('args')

        then:
        isDaemon action
    }

    def "daemon is used when command line option is used"() {
        when:
        def action = convert('--daemon', 'args')

        then:
        isDaemon action
    }

    def "does not use daemon when no-daemon command line option issued"() {
        when:
        def action = convert('--no-daemon', 'args')

        then:
        isInProcess action
    }

    def "shows status of daemons"() {
        when:
        def action = convert('--status')

        then:
        action instanceof ReportDaemonStatusAction
    }

    def "stops daemon"() {
        when:
        def action = convert('--stop')

        then:
        action instanceof StopDaemonAction
    }

    def "runs daemon in foreground"() {
        when:
        def action = convert('--foreground')

        then:
        action instanceof ForegroundDaemonAction
    }

    def "executes with single use daemon if java home is not current"() {
        given:
        def javaHome = tmpDir.file("java-home")
        javaHome.file("bin/java").createFile()
        javaHome.file("bin/java.exe").createFile()
        def jvm = Jvm.forHome(javaHome)
        propertiesToDaemonParametersConverter.convert(_, _) >> { Map p, DaemonParameters params -> params.jvm = jvm }

        when:
        def action = convert('--no-daemon')

        then:
        isSingleUseDaemon action
    }

    def convert(String... args) {
        def parser = new CommandLineParser()
        factory.configureCommandLineParser(parser)
        def cl = parser.parse(args)
        return factory.createAction(parser, cl)
    }

    void isDaemon(def action) {
        assert action instanceof RunBuildAction
        assert action.executer instanceof DaemonClient
    }

    void isInProcess(def action) {
        assert action instanceof RunBuildAction
        assert action.executer instanceof SetupLoggingActionExecuter
        assert action.executer.delegate instanceof SessionFailureReportingActionExecuter
        assert action.executer.delegate.delegate instanceof StartParamsValidatingActionExecuter
        assert action.executer.delegate.delegate.delegate instanceof GradleThreadBuildActionExecuter
        assert action.executer.delegate.delegate.delegate.delegate instanceof ServicesSetupBuildActionExecuter
        assert action.executer.delegate.delegate.delegate.delegate.delegate instanceof ContinuousBuildActionExecuter
        assert action.executer.delegate.delegate.delegate.delegate.delegate.delegate instanceof InProcessBuildActionExecuter
    }

    void isSingleUseDaemon(def action) {
        assert action instanceof RunBuildAction
        assert action.executer instanceof SingleUseDaemonClient
    }
}
