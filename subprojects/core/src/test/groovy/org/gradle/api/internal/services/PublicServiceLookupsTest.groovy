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

package org.gradle.api.internal.services

import org.gradle.api.InvalidUserDataException
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.BuildLayout
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.ProjectLayout
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.internal.service.ServiceRegistry
import org.gradle.process.ExecOperations
import spock.lang.Specification

import static org.gradle.api.internal.services.PublicServiceLookups.EntryPoint.GRADLE
import static org.gradle.api.internal.services.PublicServiceLookups.EntryPoint.PROJECT
import static org.gradle.api.internal.services.PublicServiceLookups.EntryPoint.SETTINGS
import static org.gradle.api.internal.services.PublicServiceLookups.EntryPoint.TASK

class PublicServiceLookupsTest extends Specification {

    def registry = Mock(ServiceRegistry)

    def "resolves #serviceType.simpleName from #entryPoint"() {
        def instance = Mock(serviceType)

        when:
        def result = PublicServiceLookups.lookup(serviceType, entryPoint, registry)

        then:
        1 * registry.get(serviceType) >> instance
        result == instance

        where:
        [serviceType, entryPoint] << [
            [ObjectFactory, ProviderFactory, FileSystemOperations, ArchiveOperations, ExecOperations].collectMany { type ->
                [PROJECT, TASK, SETTINGS, GRADLE].collect { [type, it] }
            },
            [[ProjectLayout, PROJECT], [ProjectLayout, TASK], [BuildLayout, SETTINGS]]
        ].collectMany { it }
    }

    def "rejects #serviceType.simpleName from #entryPoint with pointer to available scopes"() {
        when:
        PublicServiceLookups.lookup(serviceType, entryPoint, registry)

        then:
        0 * registry._
        def e = thrown(InvalidUserDataException)
        e.message == message

        where:
        serviceType   | entryPoint | message
        ProjectLayout | SETTINGS   | "org.gradle.api.file.ProjectLayout is not available in settings scripts and plugins. It is available in project scripts and plugins and tasks."
        ProjectLayout | GRADLE     | "org.gradle.api.file.ProjectLayout is not available in init scripts and Gradle plugins. It is available in project scripts and plugins and tasks."
        BuildLayout   | PROJECT    | "org.gradle.api.file.BuildLayout is not available in project scripts and plugins. It is available in settings scripts and plugins."
        BuildLayout   | TASK       | "org.gradle.api.file.BuildLayout is not available in tasks. It is available in settings scripts and plugins."
    }

    def "rejects internal or unknown type with enumeration of available services"() {
        when:
        PublicServiceLookups.lookup(ServiceRegistry, PROJECT, registry)

        then:
        0 * registry._
        def e = thrown(InvalidUserDataException)
        e.message.startsWith("org.gradle.internal.service.ServiceRegistry is not a service that is available for lookup with service().")
        e.message.contains("org.gradle.api.model.ObjectFactory")
        e.message.contains("org.gradle.process.ExecOperations")
        e.message.contains("org.gradle.api.file.ProjectLayout")
        !e.message.contains("org.gradle.api.file.BuildLayout")
    }

    def "rejects build service type with pointer to shared build services"() {
        when:
        PublicServiceLookups.lookup(SomeBuildService, TASK, registry)

        then:
        0 * registry._
        def e = thrown(InvalidUserDataException)
        e.message.contains("is a shared build service")
        e.message.contains("@ServiceReference")
    }

    def "rejects null service type"() {
        when:
        PublicServiceLookups.lookup(null, PROJECT, registry)

        then:
        0 * registry._
        def e = thrown(InvalidUserDataException)
        e.message == "The service type given to service() must not be null."
    }

    static abstract class SomeBuildService implements BuildService<BuildServiceParameters.None> {
    }
}
