/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.component.external.model


import org.gradle.api.artifacts.VersionConstraint
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.dependencies.DefaultMutableVersionConstraint
import spock.lang.Specification

abstract class ExternalDependencyDescriptorTest extends Specification {
    ModuleComponentSelector requested = DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId("org", "module"), v("1.2+"))
    def id = DefaultModuleVersionIdentifier.newId("org", "module", "1.2+")

    static VersionConstraint v(String version) {
        new DefaultMutableVersionConstraint(version)
    }

    abstract ExternalDependencyDescriptor create(ModuleComponentSelector selector)

    def "creates a copy with new requested version"() {
        def metadata = create(requested)

        given:

        when:
        def target = DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId("org", "module"), v("1.3+"))
        def copy = metadata.withRequested(target)

        then:
        copy != metadata
        copy.selector == target
    }

    def "returns a module component selector if descriptor indicates a default dependency"() {
        given:
        def metadata = create(requested)

        when:
        ComponentSelector componentSelector = metadata.getSelector()

        then:
        componentSelector instanceof ModuleComponentSelector
        componentSelector.group == 'org'
        componentSelector.module == 'module'
        componentSelector.version == '1.2+'
    }

}
