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

package org.gradle.internal.session

import org.gradle.internal.concurrent.Stoppable
import org.gradle.internal.service.DefaultServiceRegistry
import org.gradle.internal.service.ServiceRegistry
import org.gradle.internal.service.UnknownServiceException
import spock.lang.Specification

class DefaultBuildSessionTest extends Specification {
    ServiceRegistry parentRegistry = Stub(ServiceRegistry)
    BuildSession buildSession = new DefaultBuildSession(parentRegistry);

    def "returns the same build session scope until the build session is reset" () {
        def services
        def servicesBefore = buildSession.getServices()

        when:
        services = buildSession.getServices()

        then:
        services == servicesBefore

        when:
        services = buildSession.getServices()

        then:
        services == servicesBefore

        when:
        buildSession.reset()

        then:
        buildSession.getServices() != servicesBefore
    }

    def "a service added to the build session scope is stopped and removed on reset" () {
        parentRegistry.get(Stoppable) >> { throw new UnknownServiceException(Stoppable, "") }
        Stoppable service = Mock(Stoppable)
        ((DefaultServiceRegistry)buildSession.getServices()).add(Stoppable.class, service)
        assert buildSession.getServices().get(Stoppable) == service

        when:
        buildSession.reset()

        then:
        1 * service.stop()

        when:
        buildSession.getServices().get(Stoppable)

        then:
        def e = thrown(UnknownServiceException)
        e.message == "No service of type Stoppable available in BuildSessionScopeServices."
    }
}
