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

import com.google.common.collect.ImmutableSet
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyLockingProvider
import org.gradle.api.internal.attributes.AttributesSchemaInternal
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.external.model.ImmutableCapabilities
import org.gradle.internal.component.model.DependencyMetadata
import org.gradle.internal.locking.DefaultDependencyLockingState

class RootLocalComponentMetadataTest extends DefaultLocalComponentMetadataTest {
    def dependencyLockingHandler = Mock(DependencyLockingProvider)
    def metadata = new RootLocalComponentMetadata(id, componentIdentifier, "status", Mock(AttributesSchemaInternal), dependencyLockingHandler)
    private ModuleIdentifier mid = DefaultModuleIdentifier.newId('org', 'foo')

    def 'locking constraints are attached to a configuration and not its children'() {
        given:
        def constraint = DefaultModuleComponentIdentifier.newId(mid, '1.1')
        dependencyLockingHandler.loadLockState("conf") >> new DefaultDependencyLockingState(true, [constraint] as Set, {entry -> false })
        dependencyLockingHandler.loadLockState("child") >> DefaultDependencyLockingState.EMPTY_LOCK_CONSTRAINT
        addConfiguration('conf').enableLocking()
        addConfiguration('child', ['conf']).enableLocking()

        when:
        def conf = metadata.getConfiguration('conf')
        def child = metadata.getConfiguration('child')

        then:
        conf.syntheticDependencies.size() == 1
        child.syntheticDependencies.size() == 0
    }

    def 'locking constraints are not transitive'() {
        given:
        def constraint = DefaultModuleComponentIdentifier.newId(mid, '1.1')
        dependencyLockingHandler.loadLockState("conf") >> new DefaultDependencyLockingState(true, [constraint] as Set, {entry -> false })
        addConfiguration('conf').enableLocking()

        when:
        def conf = metadata.getConfiguration('conf')

        then:
        conf.syntheticDependencies.size() == 1
        conf.syntheticDependencies.each {
            assert !it.transitive
        }
    }

    def 'provides useful reason for locking constraints (#strict)'() {
        given:
        def constraint = DefaultModuleComponentIdentifier.newId(mid, '1.1')
        dependencyLockingHandler.loadLockState("conf") >> new DefaultDependencyLockingState(strict, [constraint] as Set, {entry -> false })
        addConfiguration('conf').enableLocking()

        when:
        def conf = metadata.getConfiguration('conf')

        then:
        conf.syntheticDependencies.size() == 1
        conf.syntheticDependencies.each { DependencyMetadata dep ->
            assert dep.reason == reason
        }

        where:
        reason                                                          | strict
        "dependency was locked to version '1.1'"                        | true
        "dependency was locked to version '1.1' (update/lenient mode)"  | false
    }

    private addConfiguration(String name, Collection<String> extendsFrom = [], ImmutableAttributes attributes = ImmutableAttributes.EMPTY) {
        metadata.addConfiguration(name, "", extendsFrom as Set, ImmutableSet.copyOf(extendsFrom + [name]), true, true, attributes, true, null, true, ImmutableCapabilities.EMPTY, Collections.&emptyList)
    }

}
