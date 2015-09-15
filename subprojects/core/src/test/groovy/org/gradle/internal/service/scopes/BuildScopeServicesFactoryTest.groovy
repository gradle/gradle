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

package org.gradle.internal.service.scopes

import org.gradle.StartParameter
import org.gradle.internal.service.ServiceRegistry
import spock.lang.Specification


class BuildScopeServicesFactoryTest extends Specification {
    ServiceRegistry parentRegistry = Mock(ServiceRegistry)
    BuildScopeServicesFactory factory = new BuildScopeServicesFactory(parentRegistry)

    def "creates and adds session scope registry to additionalCloseables when no session provided" () {
        when:
        def buildScopeServices = factory.create(new StartParameter())

        then:
        2 * parentRegistry.getAll(_) >> []
        buildScopeServices.additionalCloseables.size() == 1
        buildScopeServices.additionalCloseables[0] instanceof BuildSessionScopeServices
    }

    def "does not add session scope registry to additionalCloseables when session is provided" () {
        def contextServices = Mock(ServiceRegistry)

        when:
        def buildScopeServices = factory.create(contextServices)

        then:
        1 * contextServices.getAll(_) >> []
        buildScopeServices.additionalCloseables.size() == 0
    }
}
