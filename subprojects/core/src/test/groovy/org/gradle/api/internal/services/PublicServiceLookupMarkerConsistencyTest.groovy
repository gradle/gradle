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

import org.gradle.api.Task
import org.gradle.api.services.GradleService
import org.gradle.api.services.ProjectService
import org.gradle.api.services.SettingsService
import org.gradle.api.services.TaskService
import spock.lang.Specification

/**
 * Pins the two representations of "which service is available in which kind of script" together: the runtime
 * allowlist in {@link PublicServiceLookups} (authoritative) and the per-scope compile-time marker interfaces
 * that bound the Kotlin DSL {@code service<T>()} members. If one drifts from the other this fails.
 */
class PublicServiceLookupMarkerConsistencyTest extends Specification {

    private static final Map<PublicServiceLookups.EntryPoint, Class<?>> MARKERS = [
        (PublicServiceLookups.EntryPoint.PROJECT) : ProjectService,
        (PublicServiceLookups.EntryPoint.TASK)    : TaskService,
        (PublicServiceLookups.EntryPoint.SETTINGS): SettingsService,
        (PublicServiceLookups.EntryPoint.GRADLE)  : GradleService,
    ]

    def "every allowlisted service implements exactly the markers of the scopes it is available in"() {
        expect:
        PublicServiceLookups.availableServices().each { serviceType, scopes ->
            MARKERS.each { entryPoint, marker ->
                def allowed = scopes.contains(entryPoint)
                def marked = marker.isAssignableFrom(serviceType)
                assert allowed == marked:
                    "${serviceType.name} ${allowed ? 'is' : 'is not'} allowlisted for ${entryPoint} but ${marked ? 'implements' : 'does not implement'} ${marker.name}"
            }
        }
    }

    def "everything available to init scripts is also available to project build scripts and settings scripts"() {
        // A script applied with `apply from: ..., to: gradle` from a project or settings script targets the
        // Gradle instance. It must not see anything the script that applied it cannot.
        expect:
        PublicServiceLookups.availableServices().each { serviceType, scopes ->
            if (scopes.contains(PublicServiceLookups.EntryPoint.GRADLE)) {
                assert scopes.contains(PublicServiceLookups.EntryPoint.PROJECT): "${serviceType.name} is available to init scripts but not to project build scripts"
                assert scopes.contains(PublicServiceLookups.EntryPoint.SETTINGS): "${serviceType.name} is available to init scripts but not to settings scripts"
            }
        }
    }

    def "Task.service(Class) is bounded by the task marker"() {
        expect:
        Task.getMethod("service", Class).typeParameters[0].bounds[0] == TaskService
    }
}
