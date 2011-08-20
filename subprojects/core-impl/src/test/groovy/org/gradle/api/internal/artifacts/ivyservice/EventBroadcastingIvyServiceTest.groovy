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
package org.gradle.api.internal.artifacts.ivyservice

import spock.lang.Specification
import org.gradle.api.internal.artifacts.IvyService
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal
import org.gradle.api.artifacts.DependencyResolutionListener
import org.gradle.api.artifacts.ResolvableDependencies

class EventBroadcastingIvyServiceTest extends Specification {
    final IvyService target = Mock()
    final ConfigurationInternal configuration = Mock()
    final DependencyResolutionListener broadcast = Mock()
    final ResolvableDependencies dependencies = Mock()
    final EventBroadcastingIvyService ivyService = new EventBroadcastingIvyService(target)

    def "fires events before and after resolution"() {
        given:
        _ * configuration.dependencyResolutionBroadcast >> broadcast
        _ * configuration.incoming >> dependencies

        when:
        ivyService.resolve(configuration)

        then:
        1 * broadcast.beforeResolve(dependencies)

        and:
        1 * target.resolve(configuration)

        and:
        1 * broadcast.afterResolve(dependencies)
        0 * broadcast._
    }

}
