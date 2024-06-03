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

import org.gradle.api.Action
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.cli.CommandLineParser
import org.gradle.internal.Actions
import org.gradle.internal.Factory
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.logging.LoggingManagerInternal
import org.gradle.internal.logging.console.GlobalUserInputReceiver
import org.gradle.internal.logging.events.OutputEventListener
import org.gradle.internal.logging.text.StyledTextOutputFactory
import org.gradle.internal.nativeintegration.services.NativeServices
import org.gradle.internal.service.DefaultServiceRegistry
import org.gradle.internal.service.Provides
import org.gradle.internal.service.ServiceRegistrationProvider
import org.gradle.internal.service.ServiceRegistry
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.launcher.daemon.bootstrap.ForegroundDaemonAction
import org.gradle.launcher.daemon.client.DaemonClient
import org.gradle.launcher.daemon.client.SingleUseDaemonClient
import org.gradle.launcher.daemon.configuration.DaemonParameters
import org.gradle.launcher.daemon.context.DaemonRequestContext
import org.gradle.launcher.daemon.toolchain.DaemonJvmCriteria
import org.gradle.launcher.exec.BuildActionExecutor
import org.gradle.process.internal.CurrentProcess
import org.gradle.process.internal.JvmOptions
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.tooling.internal.provider.RunInProcess
import org.gradle.util.SetSystemProperties
import org.gradle.util.UsesNativeServices
import org.junit.Rule
import spock.lang.Specification

@UsesNativeServices
class BuildActionsFactoryTest extends Specification {
    @Rule
    public final SetSystemProperties sysProperties = new SetSystemProperties();
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass());
    ServiceRegistry loggingServices = new DefaultServiceRegistry()
    boolean useCurrentProcess
    BuildActionsFactory factory

    def setup() {
        def factoryLoggingManager = Mock(Factory) { _ * create() >> Mock(LoggingManagerInternal) }
        loggingServices.add(OutputEventListener, Mock(OutputEventListener))
        loggingServices.add(GlobalUserInputReceiver, Mock(GlobalUserInputReceiver))
        loggingServices.add(StyledTextOutputFactory, Mock(StyledTextOutputFactory))
        loggingServices.addProvider(new ServiceRegistrationProvider() {
            @Provides
            Factory<LoggingManagerInternal> createFactory() {
                return factoryLoggingManager
            }
        })

        factory = new BuildActionsFactory(loggingServices) {
            boolean canUseCurrentProcess(DaemonParameters daemonParameters, DaemonRequestContext requestContext) {
                return useCurrentProcess
            }
        }
    }

    def "check that --max-workers overrides org.gradle.workers.max"() {
        when:
        def action = convert('--max-workers=5')

        then:
        unwrapAction(action).startParameter.maxWorkerCount == 5
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
        given:
        useCurrentProcess = true

        when:
        def action = convert('--no-daemon', 'args')

        then:
        isInProcess action
    }

    def "shows status of daemons"() {
        when:
        def action = convert('--status')

        then:
        unwrapAction(action) instanceof ReportDaemonStatusAction
    }

    def "stops daemon"() {
        when:
        def action = convert('--stop')

        then:
        unwrapAction(action) instanceof StopDaemonAction
    }

    def "runs daemon in foreground"() {
        when:
        def action = convert('--foreground')

        then:
        unwrapAction(action) instanceof ForegroundDaemonAction
    }

    def "executes with single use daemon if current process cannot be used"() {
        given:
        useCurrentProcess = false

        when:
        def action = convert('--no-daemon')

        then:
        isSingleUseDaemon action
    }

    def "daemon context can be built from current process"() {
        def request = createDaemonRequest()
        def currentJvmOptions = new JvmOptions(Mock(FileCollectionFactory))
        currentJvmOptions.jvmArgs = []

        def daemon = BuildActionsFactory.buildDaemonContextForCurrentProcess(request, new CurrentProcess(Jvm.current(), currentJvmOptions))

        expect:
        // don't care what values these properties have
        daemon.daemonRegistryDir == null
        daemon.pid == 0L
        daemon.idleTimeout == 0
        daemon.uid

        // should report current JVM's home
        daemon.javaHome == Jvm.current().javaHome
        !daemon.shouldApplyInstrumentationAgent()
        daemon.nativeServicesMode == request.nativeServicesMode
        daemon.priority == request.priority
        daemon.daemonOpts == request.daemonOpts
    }

    private DaemonRequestContext createDaemonRequest(Collection<String> daemonOpts=[]) {
        def request = new DaemonRequestContext(null, new DaemonJvmCriteria(JavaLanguageVersion.current(), null, null), daemonOpts, false, NativeServices.NativeServicesMode.NOT_SET, DaemonParameters.Priority.NORMAL)
        request
    }

    def convert(String... args) {
        def parser = new CommandLineParser()
        factory.configureCommandLineParser(parser)
        def cl = parser.parse(args)
        return factory.createAction(parser, cl)
    }

    void isDaemon(def action) {
        def runnable = unwrapAction(action)
        def executor = unwrapExecutor(runnable)
        assert executor instanceof DaemonClient
    }

    void isInProcess(def action) {
        def runnable = unwrapAction(action)
        def executor = unwrapExecutor(runnable)
        assert executor instanceof RunInProcess
    }

    void isSingleUseDaemon(def action) {
        def runnable = unwrapAction(action)
        def executor = unwrapExecutor(runnable)
        assert executor instanceof SingleUseDaemonClient
    }

    private Runnable unwrapAction(Action<?> action) {
        assert action instanceof Actions.RunnableActionAdapter
        return action.runnable
    }

    private BuildActionExecutor unwrapExecutor(Runnable runnable) {
        assert runnable instanceof RunBuildAction
        return runnable.executor
    }
}
