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

import org.gradle.BuildResult
import org.gradle.GradleLauncher
import org.gradle.configuration.GradleLauncherMetaData
import org.gradle.initialization.GradleLauncherAction
import org.gradle.launcher.daemon.client.DaemonClient
import org.gradle.launcher.daemon.client.EmbeddedDaemonClientServices
import org.gradle.launcher.daemon.server.exec.DaemonCommandAction
import org.gradle.launcher.daemon.server.exec.DaemonCommandExecuter
import org.gradle.launcher.daemon.server.exec.DefaultDaemonCommandExecuter
import org.gradle.launcher.daemon.server.exec.ForwardClientInput
import org.gradle.launcher.exec.DefaultBuildActionParameters
import org.gradle.messaging.concurrent.ExecutorFactory
import org.gradle.util.TemporaryFolder
import org.junit.Rule
import spock.lang.Specification

/**
 * by Szczepan Faber, Luke Daley created at: 12/22/11
 */
class DaemonServerSendingBackFailureTest extends Specification {

    @Rule def temp = new TemporaryFolder()

    static class DummyLauncherAction implements GradleLauncherAction, Serializable {
        Object getResult() { null }
        BuildResult run(GradleLauncher launcher) { null }
    }

    //TODO SF merge with the other test
    def "sends back the failure when daemon failure happens after listening for input"() {
        given:
        def services = new EmbeddedDaemonClientServices() {
            DaemonCommandExecuter createDaemonCommandExecuter() {
                return new DefaultDaemonCommandExecuter(getLoggingServices(), get(ExecutorFactory.class)) {
                    List<DaemonCommandAction> createActions() {
                        def actions = super.createActions();
                        def failingAction = { throw new RuntimeException("boo!") } as DaemonCommandAction
                        //we need to inject the failing action in an appropriate place in the sequence
                        //that is after the ForwardClientInput
                        actions.add(actions.findIndexOf { it instanceof ForwardClientInput } + 1, failingAction)
                        return actions
                    }
                }
            }
        }

        def client = services.get(DaemonClient.class)
        def params = new DefaultBuildActionParameters(new GradleLauncherMetaData(), 0, System.properties, [:], temp.dir)

        when:
        client.execute(new DummyLauncherAction(), params)

        then:
        def ex = thrown(RuntimeException)
        ex.message == 'boo!'
    }
}
