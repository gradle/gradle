/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.locking

import org.gradle.api.Action
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.ResolutionStrategy
import spock.lang.Specification
import spock.lang.Subject

class DefaultDependencyLockingHandlerTest extends Specification {

    def 'activates dependency locking on configurations'() {
        given:
        ConfigurationContainer container = Mock()
        Configuration configuration = Mock()
        ResolutionStrategy strategy = Mock()
        @Subject
        def handler = new DefaultDependencyLockingHandler({container}, null)

        when:
        handler.lockAllConfigurations()

        then:
        1 * container.configureEach(_ as Action) >> { Action action ->
            action.execute(configuration)
        }

        1 * configuration.getResolutionStrategy() >> strategy
        1 * strategy.activateDependencyLocking()
    }

    def 'deactivates dependency locking on configurations'() {
        given:
        ConfigurationContainer container = Mock()
        Configuration configuration = Mock()
        ResolutionStrategy strategy = Mock()
        @Subject
        def handler = new DefaultDependencyLockingHandler({container}, null)

        when:
        handler.unlockAllConfigurations()

        then:
        1 * container.configureEach(_ as Action) >> { Action action ->
            action.execute(configuration)
        }

        1 * configuration.getResolutionStrategy() >> strategy
        1 * strategy.deactivateDependencyLocking()
    }

}
