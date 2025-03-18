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

package org.gradle.api.internal.file.collections

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.groovy.support.CompoundAssignmentBinaryCompatibilityFixture
import spock.lang.Specification

class FileCollectionExtensionsApiTest extends Specification implements CompoundAssignmentBinaryCompatibilityFixture {
    // This class verifies binary compatibility of non-public API used by Groovy static compilation.
    // String are used intentionally to catch unexpected change to class name that breaks binary compatibility.
    private static final String EXTENSION_CLASS = "org.gradle.api.internal.file.collections.FileCollectionExtensions"
    private static final String STAND_IN_CLASS = "org.gradle.api.internal.file.collections.ConfigurableFileCollectionCompoundAssignmentStandIn"

    def "extension class has expected api"() {
        given:
        def extensionClass = Class.forName(EXTENSION_CLASS)
        def standInClass = Class.forName(STAND_IN_CLASS)

        expect:
        assertHasForCompoundAssignmentMethod(extensionClass, ConfigurableFileCollection, standInClass)
    }

    def "stand-in class defines operators"() {
        given:
        def standInClass = Class.forName(STAND_IN_CLASS)

        expect:
        assertHasOperator(standInClass, OP_PLUS, FileCollection, FileCollection)
    }
}
