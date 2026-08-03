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

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.initialization.Settings
import org.gradle.api.invocation.Gradle
import org.gradle.api.services.GradleService
import org.gradle.api.services.ProjectService
import org.gradle.api.services.SettingsService
import org.gradle.api.services.TaskService
import spock.lang.Specification

/**
 * Pins the two representations of "which service is available in which scope" together: the runtime
 * allowlist in {@link PublicServiceLookups} (authoritative), the per-scope compile-time marker interfaces,
 * and the generic bound on each {@code service(Class)} member. If any drifts from the others this fails.
 */
class PublicServiceLookupMarkerConsistencyTest extends Specification {

    // entry point -> [host interface declaring service(Class), scope marker]
    private static final Map<PublicServiceLookups.EntryPoint, List<Class<?>>> SCOPES = [
        (PublicServiceLookups.EntryPoint.PROJECT) : [Project, ProjectService],
        (PublicServiceLookups.EntryPoint.TASK)    : [Task, TaskService],
        (PublicServiceLookups.EntryPoint.SETTINGS): [Settings, SettingsService],
        (PublicServiceLookups.EntryPoint.GRADLE)  : [Gradle, GradleService],
    ]

    def "every allowlisted service implements the marker for each scope it is available in"() {
        expect:
        PublicServiceLookups.availableServices().each { serviceType, scopes ->
            scopes.each { entryPoint ->
                def marker = SCOPES[entryPoint][1]
                assert marker.isAssignableFrom(serviceType):
                    "${serviceType.name} is allowlisted for ${entryPoint} but does not implement ${marker.name}"
            }
        }
    }

    def "each service(Class) member is bounded by its scope marker"() {
        expect:
        SCOPES.each { entryPoint, hostAndMarker ->
            def host = hostAndMarker[0]
            def marker = hostAndMarker[1]
            def bound = host.getMethod("service", Class).typeParameters[0].bounds[0]
            assert bound == marker:
                "${host.name}.service(Class) is bounded by ${bound}, expected ${marker.name}"
        }
    }
}
