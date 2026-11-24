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

package org.gradle.internal.operations

import spock.lang.Specification

class BuildOperationDescriptorTest extends Specification {

    def "display name and name have the owner to show"() {
        def descriptor = BuildOperationDescriptor.displayName("Evaluate settings").owner(owner).build()

        expect:
        descriptor.displayName == displayName
        descriptor.name == displayName

        where:
        owner         | displayName
        owner(":lib") | "Evaluate settings (:lib)"
        owner(null)   | "Evaluate settings"
        null          | "Evaluate settings"
    }

    def "a name that is set has no owner"() {
        def descriptor = BuildOperationDescriptor.displayName("Evaluate settings").name("Settings").owner(owner(":lib")).build()

        expect:
        descriptor.displayName == "Evaluate settings (:lib)"
        descriptor.name == "Settings"
    }

    def "progress display name has the owner to show"() {
        def descriptor = BuildOperationDescriptor.displayName("Evaluate settings").progressDisplayName("Evaluating settings").owner(owner).build()

        expect:
        descriptor.progressDisplayName == progressDisplayName

        where:
        owner         | progressDisplayName
        owner(":lib") | ProgressDisplayName.of(":lib", "Evaluating settings")
        owner(null)   | ProgressDisplayName.of("Evaluating settings")
        null          | ProgressDisplayName.of("Evaluating settings")
    }

    def "an owner without a progress display name is only in the display name"() {
        def descriptor = BuildOperationDescriptor.displayName("Load build").owner(owner(":lib")).build()

        expect:
        descriptor.displayName == "Load build (:lib)"
        descriptor.progressDisplayName == null
    }

    private static BuildOperationOwner owner(String ownerToShow) {
        return { -> ownerToShow } as BuildOperationOwner
    }
}
