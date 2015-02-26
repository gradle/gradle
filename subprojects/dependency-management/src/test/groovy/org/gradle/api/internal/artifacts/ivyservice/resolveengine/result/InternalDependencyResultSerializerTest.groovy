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

import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector
import org.gradle.internal.resolve.ModuleVersionResolveException
import org.gradle.internal.serialize.InputStreamBackedDecoder
import org.gradle.internal.serialize.OutputStreamBackedEncoder
import spock.lang.Specification

import static org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier.newId
import static org.gradle.api.internal.artifacts.DefaultModuleVersionSelector.newSelector

class InternalDependencyResultSerializerTest extends Specification {

    def serializer = new InternalDependencyResultSerializer()

    def "serializes successful dependency result"() {
        def successful = Mock(InternalDependencyResult) {
            getRequested() >> DefaultModuleComponentSelector.newSelector("org", "foo", "1.0")
            getFailure() >> null
            getSelected() >> newId("org", "foo", "1.0")
            getReason() >> VersionSelectionReasons.REQUESTED
        }

        when:
        def bytes = new ByteArrayOutputStream()
        def encoder = new OutputStreamBackedEncoder(bytes)
        serializer.write(encoder, successful)
        encoder.flush()
        def out = serializer.read(new InputStreamBackedDecoder(new ByteArrayInputStream(bytes.toByteArray())), [:])

        then:
        out.requested == DefaultModuleComponentSelector.newSelector("org", "foo", "1.0")
        out.failure == null
        out.selected == newId("org", "foo", "1.0")
    }

    def "serializes failed dependency result"() {
        ModuleComponentSelector requested = DefaultModuleComponentSelector.newSelector("x", "y", "1.0")
        def failure = new ModuleVersionResolveException(newSelector("x", "y", "1.2"), new RuntimeException("Boo!"))

        def failed = Mock(InternalDependencyResult) {
            getRequested() >> requested
            getFailure() >> failure
            getSelected() >> null
            getReason() >> VersionSelectionReasons.CONFLICT_RESOLUTION
        }

        when:
        def bytes = new ByteArrayOutputStream()
        def encoder = new OutputStreamBackedEncoder(bytes)
        serializer.write(encoder, failed)
        encoder.flush()
        Map<ModuleComponentSelector, ModuleVersionResolveException> map = new HashMap<>()
        map.put(requested, failure)
        def out = serializer.read(new InputStreamBackedDecoder(new ByteArrayInputStream(bytes.toByteArray())), map)

        then:
        out.requested == DefaultModuleComponentSelector.newSelector("x", "y", "1.0")
        out.failure.cause.message == "Boo!"
        out.selected == null
        out.reason == VersionSelectionReasons.CONFLICT_RESOLUTION
    }
}
