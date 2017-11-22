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
import org.gradle.api.internal.artifacts.dependencies.DefaultMutableVersionConstraint
import org.gradle.internal.component.external.descriptor.Configuration
import org.gradle.internal.component.model.DependencyMetadata
import org.gradle.internal.component.model.ModuleSource
import spock.lang.Specification

import static org.gradle.internal.component.external.model.DefaultModuleComponentSelector.newSelector

abstract class AbstractModuleComponentResolveMetadataTest extends Specification {

    def id = DefaultModuleComponentIdentifier.newId("group", "module", "version")
    def configurations = []
    def dependencies = []

    abstract AbstractModuleComponentResolveMetadata createMetadata(ModuleComponentIdentifier id, List<Configuration> configurations, List<DependencyMetadata> dependencies)

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

    def "builds and caches dependencies for a configuration"() {
        given:
        configuration("compile")
        configuration("runtime", ["compile"])
        dependency("org", "module", "1.1", "runtime", "a")
        dependency("org", "module", "1.2", "*", "b")
        dependency("org", "module", "1.3", "compile", "c")
        dependency("org", "module", "1.4", "other", "d")
        dependency("org", "module", "1.5", "%", "e")

        when:
        def md = metadata
        def runtime = md.getConfiguration("runtime")
        def compile = md.getConfiguration("compile")

        then:
        runtime.dependencies*.selector*.versionConstraint.preferredVersion == ["1.1", "1.2", "1.3", "1.5"]
        runtime.dependencies.is(runtime.dependencies)

        compile.dependencies*.selector*.versionConstraint.preferredVersion == ["1.2", "1.3", "1.5"]
        compile.dependencies.is(compile.dependencies)
    }

    def "can make a copy with different source"() {
        given:
        configuration("compile")
        def source = Stub(ModuleSource)

        def metadata = getMetadata()
        // Prime the configuration
        metadata.getConfiguration("compile")

        when:
        def copy = metadata.withSource(source)

        then:
        copy.source == source
        copy.configurationNames == metadata.configurationNames
        copy.getConfiguration("compile").is(metadata.getConfiguration("compile"))
        copy.dependencies.is(metadata.dependencies)
    }

    def configuration(String name, List<String> extendsFrom = []) {
        configurations.add(new Configuration(name, true, true, extendsFrom))
    }

    def dependency(String org, String module, String version) {
        dependencies.add(new IvyDependencyMetadata(newSelector(org, module, new DefaultMutableVersionConstraint(version)), ImmutableListMultimap.of()))
    }

    def dependency(String org, String module, String version, String fromConf, String toConf) {
        dependencies.add(new IvyDependencyMetadata(newSelector(org, module, new DefaultMutableVersionConstraint(version)), ImmutableListMultimap.of(fromConf, toConf)))
    }
}
