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
import org.gradle.initialization.BuildCancellationToken
import org.gradle.initialization.BuildRequestMetaData
import org.gradle.initialization.DefaultBuildRequestContext
import org.gradle.initialization.NoOpBuildEventConsumer
import org.gradle.internal.invocation.BuildAction
import org.gradle.launcher.continuous.DefaultTriggerDetails
import org.gradle.launcher.continuous.TriggerGenerator
import org.gradle.launcher.continuous.TriggerGeneratorFactory
import org.gradle.launcher.continuous.TriggerListener
import org.gradle.util.Clock
import spock.lang.Specification

class ContinuousModeBuildActionExecuterTest extends Specification {

    def underlyingExecuter = Mock(BuildActionExecuter)
    def triggerGenerator = Mock(TriggerGenerator)

    def triggerGeneratorFactory = new TriggerGeneratorFactory() {
        TriggerGenerator newInstance(TriggerListener listener) {
            return triggerGenerator
        }
    }
    def action = Mock(BuildAction)
    def cancellationToken = Mock(BuildCancellationToken)
    def clock = Mock(Clock)
    def requestMetadata = Stub(BuildRequestMetaData) {
        getBuildTimeClock() >> clock
    }
    def requestContext = new DefaultBuildRequestContext(requestMetadata, cancellationToken, new NoOpBuildEventConsumer())
    def actionParameters = Mock(BuildActionParameters)

    def executer = new ContinuousModeBuildActionExecuter(underlyingExecuter, triggerGeneratorFactory)

    def "uses underlying executer when continuous mode is not enabled"() {
        given:
        actionParameters.continuousModeEnabled >> false

        when:
        executer.execute(action, requestContext, actionParameters)

        then:
        1 * underlyingExecuter.execute(action, requestContext, actionParameters)
    }

    def "allows exceptions to propagate for single builds"() {
        given:
        actionParameters.continuousModeEnabled >> false

        when:
        executer.execute(action, requestContext, actionParameters)

        then:
        1 * underlyingExecuter.execute(action, requestContext, actionParameters) >> { throw new RuntimeException("always fails") }
        thrown(RuntimeException)
    }

    def "runs once and is cancelled in continuous mode"() {
        given:
        def trigger = new DefaultTriggerDetails("test")
        actionParameters.continuousModeEnabled >> true
        cancellationToken.cancellationRequested >>> [ false, true ]

        when:
        executer.execute(action, requestContext, actionParameters)

        then:
        1 * underlyingExecuter.execute(action, requestContext, actionParameters) >> { executer.triggered(trigger) }
        1 * clock.reset()
    }
}
