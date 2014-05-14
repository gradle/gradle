/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.component

import org.gradle.api.component.Artifact
import org.gradle.api.component.Component
import spock.lang.Specification

class DefaultComponentTypeRegistryTest extends Specification {

    def registry = new DefaultComponentTypeRegistry()

    def "fails getting registration for unregistered component"() {
        when:
        registry.getComponentRegistration(MyComponentType)

        then:
        def e = thrown IllegalArgumentException
        e.message == "Not a registered component type: ${MyComponentType.name}."
    }

    def "cannot register same component type twice"() {
        given:
        registry.registerComponentType(MyComponentType)

        when:
        registry.registerComponentType(MyComponentType)

        then:
        def e = thrown IllegalStateException
        e.message == "Component type ${MyComponentType.name} is already registered."
    }

    def "fails getting type registration for unregistered type"() {
        given:
        registry.registerComponentType(MyComponentType)

        when:
        registry.getComponentRegistration(MyComponentType).getArtifactType(MyArtifactType)

        then:
        def e = thrown IllegalArgumentException
        e.message == "Artifact type ${MyArtifactType.name} is not registered for component type ${MyComponentType.name}."
    }

    def "cannot register same artifact type twice"() {
        given:
        def componentRegistration = registry.registerComponentType(MyComponentType)
        componentRegistration.registerArtifactType(MyArtifactType, ArtifactType.SOURCES)

        when:
        componentRegistration.registerArtifactType(MyArtifactType, ArtifactType.SOURCES)

        then:
        def e = thrown IllegalStateException
        e.message == "Artifact type ${MyArtifactType.name} is already registered for component type ${MyComponentType.name}."
    }

    def "returns registered type for component"() {
        when:
        registry.registerComponentType(MyComponentType).registerArtifactType(MyArtifactType, ArtifactType.SOURCES)

        then:
        registry.getComponentRegistration(MyComponentType).getArtifactType(MyArtifactType) == ArtifactType.SOURCES
    }

    interface MyComponentType extends Component {}
    interface MyArtifactType extends Artifact {}
}
