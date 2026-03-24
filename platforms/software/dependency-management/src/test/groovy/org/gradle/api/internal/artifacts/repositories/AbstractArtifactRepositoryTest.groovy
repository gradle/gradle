/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.artifacts.repositories

import org.gradle.api.artifacts.repositories.RepositoryResourceAccessor
import org.gradle.api.model.ObjectFactory
import org.gradle.internal.service.ServiceLookup
import org.gradle.internal.service.UnknownServiceException
import spock.lang.Specification

import javax.inject.Inject

class AbstractArtifactRepositoryTest extends Specification {

    def objectFactory = Mock(ObjectFactory)

    def "service lookup provides ObjectFactory"() {
        given:
        def services = createServiceLookup(null)

        expect:
        services.get(ObjectFactory) == objectFactory
    }

    def "service lookup provides RepositoryResourceAccessor when available"() {
        given:
        def accessor = Mock(RepositoryResourceAccessor)
        def services = createServiceLookup(accessor)

        expect:
        services.get(RepositoryResourceAccessor) == accessor
    }

    def "service lookup throws when RepositoryResourceAccessor is requested but repository has no URL"() {
        given:
        def services = createServiceLookup(null)

        when:
        services.find(RepositoryResourceAccessor)

        then:
        def e = thrown(Exception)
        e.message == "Can not inject RepositoryResourceAccessor since repository has no URL."
    }

    def "service lookup throws for unknown service type listing available services (#description)"() {
        given:
        def services = createServiceLookup(hasAccessor ? Mock(RepositoryResourceAccessor) : null)

        when:
        services.get(String)

        then:
        def e = thrown(UnknownServiceException)
        e.message.contains("is not available for repository metadata rules")
        e.message.contains("Available services: $expectedServices")

        where:
        hasAccessor | expectedServices
        false       | "ObjectFactory"
        true        | "ObjectFactory, RepositoryResourceAccessor"

        description = hasAccessor ? "with RepositoryResourceAccessor" : "without RepositoryResourceAccessor"
    }

    def "service lookup throws for annotated service type listing available services"() {
        given:
        def services = createServiceLookup(Mock(RepositoryResourceAccessor))

        when:
        services.get(String, Inject)

        then:
        def e = thrown(UnknownServiceException)
        e.message.contains("annotated with @Inject")
        e.message.contains("is not available for repository metadata rules")
        e.message.contains("Available services: ObjectFactory, RepositoryResourceAccessor")
    }

    private ServiceLookup createServiceLookup(RepositoryResourceAccessor repositoryResourceAccessor) {
        new AbstractArtifactRepository.RepositoryRuleServiceLookup(objectFactory, repositoryResourceAccessor)
    }
}
