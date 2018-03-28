/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.component.local.model

import org.gradle.api.internal.artifacts.dependencies.DefaultDependencyConstraint
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyLockingProvider
import org.gradle.api.internal.attributes.AttributeContainerInternal
import org.gradle.api.internal.attributes.AttributesSchemaInternal
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.internal.component.external.model.ImmutableCapabilities

class RootLocalComponentMetadataTest extends DefaultLocalComponentMetadataTest {
    def dependencyLockingHandler = Mock(DependencyLockingProvider)
    def metadata = new RootLocalComponentMetadata(id, componentIdentifier, "status", Mock(AttributesSchemaInternal), dependencyLockingHandler)

    def 'locking constraints are attached to a configuration and not its children'() {
        given:
        def constraint = new DefaultDependencyConstraint('org', 'foo', '1.1')
        dependencyLockingHandler.findLockedDependencies("conf") >> ([constraint] as Set)
        dependencyLockingHandler.findLockedDependencies("child") >> Collections.emptySet()
        addConfiguration('conf')
        addConfiguration('child', ['conf'])

        when:
        def conf = metadata.getConfiguration('conf')
        def child = metadata.getConfiguration('child')

        then:
        conf.dependencies.size() == 1
        child.dependencies.size() == 0
    }

    private addConfiguration(String name, Collection<String> extendsFrom = [], AttributeContainerInternal attributes = ImmutableAttributes.EMPTY) {
        metadata.addConfiguration(name, "", extendsFrom as Set, (extendsFrom + [name]) as Set, true, true, attributes, true, true, ImmutableCapabilities.EMPTY, true)
    }

}
