/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.launcher.daemon.server

import org.gradle.api.logging.LogLevel
import org.gradle.configuration.GradleLauncherMetaData
import org.gradle.initialization.BuildController
import org.gradle.initialization.DefaultGradleLauncherFactory
import org.gradle.initialization.BuildAction
import org.gradle.internal.nativeplatform.ProcessEnvironment
import org.gradle.launcher.daemon.client.DaemonClient
import org.gradle.launcher.daemon.client.EmbeddedDaemonClientServices
import org.gradle.launcher.daemon.context.DaemonContext
import org.gradle.launcher.daemon.server.exec.DaemonCommandAction
import org.gradle.launcher.daemon.server.exec.DaemonCommandExecuter
import org.gradle.launcher.daemon.server.exec.DefaultDaemonCommandExecuter
import org.gradle.launcher.daemon.server.exec.ForwardClientInput
import org.gradle.launcher.exec.DefaultBuildActionParameters
import org.gradle.logging.LoggingManagerInternal
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

/**
 * by Szczepan Faber, created at: 12/21/11
 */
class DaemonServerExceptionHandlingTest extends Specification {

    @Rule TestNameTestDirectoryProvider temp = new TestNameTestDirectoryProvider()
    def parameters = new DefaultBuildActionParameters(new GradleLauncherMetaData(), 0, new HashMap(System.properties), [:], temp.testDirectory, LogLevel.ERROR)

    static class DummyLauncherAction implements BuildAction, Serializable {
        Object someState
        Object run(BuildController buildController) { null }
    }

    def "sends back failure when the daemon cannot receive the first command"() {
        given:
        def client = new EmbeddedDaemonClientServices().get(DaemonClient)

        def clz = new GroovyClassLoader().parseClass("class Foo implements Serializable {}")
        def unloadableClass = clz.newInstance()
        //the action contains some state that cannot be deserialized on the daemon side
        //this a real-world scenario, the tooling api can ask the daemon to build model
        //that does not exist with given daemon version
        def action = new DummyLauncherAction(someState: unloadableClass)

        when:
        client.execute(action, parameters)

        then:
        def ex = thrown(Exception)
        ex.message.contains("Unable to receive command from connection")
    }

    EmbeddedDaemonClientServices servicesWith(Closure configureDeamonActions) {
        //we need to override some methods to inject a failure action into the sequence
        def services = new EmbeddedDaemonClientServices() {
            DaemonCommandExecuter createDaemonCommandExecuter() {
                return new DefaultDaemonCommandExecuter(new DefaultGradleLauncherFactory(loggingServices),
                        get(ProcessEnvironment), loggingServices.getFactory(LoggingManagerInternal.class).create(), new File("dummy")) {
                    List<DaemonCommandAction> createActions(DaemonContext daemonContext) {
                        def actions = new LinkedList(super.createActions(daemonContext));
                        configureDeamonActions(actions);
                        return actions
                    }
                }
            }
        }
        services
    }

    def "sends back any Throwable when daemon failure happens after listening for input"() {
        given:
        def services = servicesWith { daemonActions ->
            def throwsError = { throw new Error("boo!") } as DaemonCommandAction
            //we need to inject the failing action in an appropriate place in the sequence
            //that is after the ForwardClientInput
            daemonActions.add(daemonActions.findIndexOf { it instanceof ForwardClientInput } + 1, throwsError)
        }

        when:
        services.get(DaemonClient).execute(new DummyLauncherAction(), parameters)

        then:
        def ex = thrown(Throwable)
        ex.message.contains('boo!')
    }

    def "reports any Throwable that might happen before client receives the output"() {
        given:
        //we need to override some methods to inject a failure action into the sequence
        def services = servicesWith { daemonActions ->
            def oome = { throw new OutOfMemoryError("Buy more ram, dude!") } as DaemonCommandAction
            daemonActions.add(0, oome)
        }

        when:
        services.get(DaemonClient).execute(new DummyLauncherAction(), parameters)

        then:
        def ex = thrown(RuntimeException)
        ex.cause.message.contains 'Buy more ram'
    }
}
