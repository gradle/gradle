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

package org.gradle.internal.component.external.model

import com.google.common.collect.ImmutableListMultimap
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.dependencies.DefaultMutableVersionConstraint
import org.gradle.internal.component.external.descriptor.Configuration
import org.gradle.internal.component.external.model.ivy.IvyDependencyDescriptor
import spock.lang.Specification

import static org.gradle.internal.component.external.model.DefaultModuleComponentSelector.newSelector

abstract class AbstractLazyModuleComponentResolveMetadataTest extends Specification {

    def id = DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("group", "module"), "version")
    def configurations = []
    def dependencies = []

    abstract ModuleComponentResolveMetadata createMetadata(ModuleComponentIdentifier id, List<Configuration> configurations, List dependencies)

    ModuleComponentResolveMetadata getMetadata() {
        return createMetadata(id, configurations, dependencies)
    }

    def "has useful string representation"() {
        given:
        configuration("runtime")

        expect:
        metadata.toString() == 'group:module:version'
        metadata.getConfiguration('runtime').toString() == 'group:module:version configuration runtime'
    }

    def "returns null for unknown configuration"() {
        expect:
        metadata.getConfiguration("conf") == null
    }

    def configuration(String name, List<String> extendsFrom = []) {
        configurations.add(new Configuration(name, true, true, extendsFrom))
    }

    def dependency(String org, String module, String version) {
        dependencies.add(new IvyDependencyDescriptor(newSelector(org, module, new DefaultMutableVersionConstraint(version)), ImmutableListMultimap.of()))
    }
}
