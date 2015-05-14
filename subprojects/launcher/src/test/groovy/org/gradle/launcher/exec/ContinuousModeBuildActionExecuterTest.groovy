/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.launcher.exec

import org.gradle.initialization.*
import org.gradle.internal.BiAction
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.invocation.BuildAction
import org.gradle.util.Clock
import spock.lang.Specification

class ContinuousModeBuildActionExecuterTest extends Specification {
    def underlyingExecuter = new UnderlyingExecuter()
    def action = Mock(BuildAction)
    def cancellationToken = Stub(BuildCancellationToken)
    def clock = Mock(Clock)
    def requestMetadata = Stub(BuildRequestMetaData)
    def requestContext = new DefaultBuildRequestContext(requestMetadata, cancellationToken, new NoOpBuildEventConsumer())
    def actionParameters = Stub(BuildActionParameters)
    def waiter = Mock(BiAction)
    def listenerManager = Mock(ListenerManager)
    def executer = new ContinuousModeBuildActionExecuter(underlyingExecuter, listenerManager, waiter)

    def setup() {
        requestMetadata.getBuildTimeClock() >> clock
    }

    def "uses underlying executer when continuous mode is not enabled"() {
        given:
        singleBuildMode()
        underlyingExecuter.with {
            first { succeeds() }
            thenStops()
        }
        when:
        executeBuild()
        then:
        underlyingExecuter.executedAllActions()
        noExceptionThrown()
    }

    def "allows exceptions to propagate for single builds"() {
        given:
        singleBuildMode()
        underlyingExecuter.with {
            first { fails() }
            thenStops()
        }
        when:
        executeBuild()
        then:
        underlyingExecuter.executedAllActions()
        thrown(RuntimeException)
    }

    def "waits for trigger in continuous mode when build works"() {
        given:
        continuousMode()
        when:
        underlyingExecuter.with {
            first { succeeds() }
            thenStops()
        }
        executeBuild()
        then:
        1 * waiter.execute(_, _)
        0 * clock.reset()
        underlyingExecuter.executedAllActions()
    }

    def "waits for trigger in continuous mode when build fails"() {
        given:
        continuousMode()
        when:
        underlyingExecuter.with {
            first { fails() }
            thenStops()
        }
        executeBuild()
        then: "hides exceptions"
        1 * waiter.execute(_, _)
        underlyingExecuter.executedAllActions()
    }

    def "keeps running after failures in continuous mode"() {
        given:
        continuousMode()
        underlyingExecuter.with {
            first { succeeds() }
            then { fails() }
            then { succeeds() }
            thenStops()
        }
        when:
        executeBuild()
        then:
        3 * waiter.execute(_, _)
        2 * clock.reset()
        underlyingExecuter.executedAllActions()
    }

    private void singleBuildMode() {
        actionParameters.continuousModeEnabled >> false
    }

    private void continuousMode() {
        actionParameters.continuousModeEnabled >> true
    }

    private void executeBuild() {
        executer.execute(action, requestContext, actionParameters)
    }

    private void succeeds() {}

    private void fails() {
        throw new RuntimeException("always fails")
    }

    private void cancelAfter(int times) {
        def keepGoing = [false]
        def thenCancel = [true]
        // cancellation request is checked twice per build
        cancellationToken.cancellationRequested >>> (keepGoing * times * 2) + thenCancel
    }

    private class UnderlyingExecuter implements BuildActionExecuter<BuildActionParameters> {
        private final List<Closure> actions = []

        UnderlyingExecuter first(Closure c) {
            actions.add(0, c)
            return this
        }

        UnderlyingExecuter then(Closure c) {
            actions.add(c)
            return this
        }

        void thenStops() {
            cancelAfter(actions.size())
        }

        boolean executedAllActions() {
            actions.empty
        }

        @Override
        Object execute(BuildAction action, BuildRequestContext requestContext, BuildActionParameters actionParameters) {
            if (actions.empty) {
                throw new IllegalArgumentException("Not enough actions for underlying executer")
            }
            actions.remove(0).call()
            return null
        }
    }
}
