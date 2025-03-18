/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.internal.provider


import org.gradle.api.internal.groovy.support.CompoundAssignmentBinaryCompatibilityFixture
import org.gradle.api.provider.HasMultipleValues
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Provider
import spock.lang.Specification

class PropertyExtensionsApiTest extends Specification implements CompoundAssignmentBinaryCompatibilityFixture {
    // This class verifies binary compatibility of non-public API used by Groovy static compilation
    // String are used intentionally to catch unexpected change to class name that breaks binary compatibility.
    private static final String COLLECTION_EXTENSION_CLASS = "org.gradle.api.internal.provider.CollectionPropertyExtensions"
    private static final String COLLECTION_STAND_IN_CLASS = "org.gradle.api.internal.provider.CollectionPropertyCompoundAssignmentStandIn"
    private static final String MAP_EXTENSION_CLASS = "org.gradle.api.internal.provider.MapPropertyExtensions"
    private static final String MAP_STAND_IN_CLASS = "org.gradle.api.internal.provider.MapPropertyCompoundAssignmentStandIn"

    def "extension class for HasMultipleValues has expected api"() {
        given:
        def extensionClass = Class.forName(COLLECTION_EXTENSION_CLASS)
        def standInClass = Class.forName(COLLECTION_STAND_IN_CLASS)

        expect:
        assertHasForCompoundAssignmentMethod(extensionClass, HasMultipleValues, standInClass)
    }

    def "stand-in class for HasMultipleValues defines operators"() {
        given:
        def standInClass = Class.forName(COLLECTION_STAND_IN_CLASS)

        expect:
        assertHasOperator(standInClass, OP_PLUS, Iterable, Provider)
        assertHasOperator(standInClass, OP_PLUS, Object[], Provider)
        assertHasOperator(standInClass, OP_PLUS, Provider, Provider)
        assertHasOperator(standInClass, OP_PLUS, Object, Provider)
    }

    def "extension class for MapProperty has expected api"() {
        given:
        def extensionClass = Class.forName(MAP_EXTENSION_CLASS)
        def standInClass = Class.forName(MAP_STAND_IN_CLASS)

        expect:
        assertHasForCompoundAssignmentMethod(extensionClass, MapProperty, standInClass)
    }

    def "stand-in class for MapProperty defines operators"() {
        given:
        def standInClass = Class.forName(MAP_STAND_IN_CLASS)

        expect:
        assertHasOperator(standInClass, OP_PLUS, Map, Provider)
        assertHasOperator(standInClass, OP_PLUS, Provider, Provider)
    }
}
