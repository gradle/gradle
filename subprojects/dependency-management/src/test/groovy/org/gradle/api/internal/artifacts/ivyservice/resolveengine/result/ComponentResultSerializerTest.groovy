/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.result

import org.gradle.api.internal.artifacts.DefaultImmutableModuleIdentifierFactory
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.serialize.SerializerSpec

import static org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier.newId

class ComponentResultSerializerTest extends SerializerSpec {

    def serializer = new ComponentResultSerializer(new DefaultImmutableModuleIdentifierFactory())

    def "serializes"() {
        def componentIdentifier = new DefaultModuleComponentIdentifier('group', 'module', 'version')
        def selection = new DefaultComponentResult(12L, newId("org", "foo", "2.0"), VersionSelectionReasons.REQUESTED, componentIdentifier)

        when:
        def result = serialize(selection, serializer)

        then:
        result.resultId == 12L
        result.selectionReason == VersionSelectionReasons.REQUESTED
        result.moduleVersion == newId("org", "foo", "2.0")
        result.componentId == componentIdentifier
    }
}
