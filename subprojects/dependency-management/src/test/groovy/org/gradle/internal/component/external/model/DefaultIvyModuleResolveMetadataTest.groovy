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

package org.gradle.internal.component.external.model

import com.google.common.collect.ImmutableListMultimap
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.artifacts.DefaultImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.DependencyManagementTestUtil
import org.gradle.api.internal.artifacts.dependencies.DefaultMutableVersionConstraint
import org.gradle.api.internal.artifacts.repositories.metadata.IvyMutableModuleMetadataFactory
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.internal.component.external.descriptor.Artifact
import org.gradle.internal.component.external.descriptor.Configuration
import org.gradle.internal.component.external.descriptor.DefaultExclude
import org.gradle.internal.component.external.model.ivy.IvyDependencyDescriptor
import org.gradle.internal.component.model.DefaultIvyArtifactName
import org.gradle.internal.component.model.Exclude
import org.gradle.util.AttributeTestUtil

import static org.gradle.internal.component.external.model.DefaultModuleComponentSelector.newSelector

class DefaultIvyModuleResolveMetadataTest extends AbstractLazyModuleComponentResolveMetadataTest {
    def ivyMetadataFactory = new IvyMutableModuleMetadataFactory(new DefaultImmutableModuleIdentifierFactory(), AttributeTestUtil.attributesFactory(), DependencyManagementTestUtil.defaultSchema())

    @Override
    ModuleComponentResolveMetadata createMetadata(ModuleComponentIdentifier id, List<Configuration> configurations, List dependencies) {
        ivyMetadataFactory.create(id, dependencies, configurations, artifacts, excludes).asImmutable()
    }

    List<Artifact> artifacts = []
    List<Exclude> excludes = []

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
        runtime.dependencies*.selector*.version == ["1.1", "1.2", "1.3", "1.5"]
        runtime.dependencies.is(runtime.dependencies)

        compile.dependencies*.selector*.version == ["1.2", "1.3", "1.5"]
        compile.dependencies.is(compile.dependencies)
    }

    def "builds and caches the configuration meta-data from the module descriptor"() {
        when:
        configuration("conf")

        then:
        metadata.getConfiguration("conf").transitive
        metadata.getConfiguration("conf").visible
    }

    def "builds and caches hierarchy for a configuration"() {
        given:
        configuration("a")
        configuration("b", ["a"])
        configuration("c", ["a"])
        configuration("d", ["b", "c"])

        when:
        def md = metadata

        then:
        md.getConfiguration("a").hierarchy as List == ["a"]
        md.getConfiguration("b").hierarchy as List  == ["b", "a"]
        md.getConfiguration("c").hierarchy as List  == ["c", "a"]
        md.getConfiguration("d").hierarchy as List == ["d", "b", "a", "c"]
    }

    def "builds and caches artifacts for a configuration"() {
        given:
        configuration("runtime")
        artifact("one", ["runtime"])
        artifact("two", ["runtime"])

        when:
        def runtime = metadata.getConfiguration("runtime")

        then:
        runtime.artifacts*.name.name == ["one", "two"]
        runtime.artifacts.is(runtime.artifacts)
    }

    def "each configuration contains a single variant containing the status attribute and the artifacts of the configuration"() {
        given:
        configuration("runtime")
        artifact("one", ["runtime"])
        artifact("two", ["runtime"])

        when:
        def runtime = metadata.getConfiguration("runtime")

        then:
        runtime.variants.size() == 1
        runtime.variants.first().attributes.keySet().size() == 1
        runtime.variants.first().attributes.getAttribute(ProjectInternal.STATUS_ATTRIBUTE) == 'integration'
        runtime.variants.first().artifacts*.name.name == ["one", "two"]
    }

    def "artifacts include union of those inherited from other configurations"() {
        given:
        configuration("compile")
        configuration("runtime", ["compile"])
        artifact("one", ["runtime"])
        artifact("two", ["runtime", "compile"])
        artifact("three", ["compile"])

        when:
        def artifacts = metadata.getConfiguration("runtime").artifacts

        then:
        artifacts*.name.name == ["one", "two", "three"]
    }

    def "builds and caches exclude rules for a configuration"() {
        given:
        configuration("compile")
        configuration("runtime", ["compile"])
        def rule1 = exclude("one", ["runtime"])
        def rule2 = exclude("two", ["compile"])
        def rule3 = exclude("three", ["other"])

        expect:
        def config = metadata.getConfiguration("runtime")
        def excludes = config.excludes
        excludes == [rule1, rule2]
        config.excludes.is(excludes)
    }

    def dependency(String org, String module, String version, String fromConf, String toConf) {
        dependencies.add(new IvyDependencyDescriptor(newSelector(DefaultModuleIdentifier.newId(org, module), new DefaultMutableVersionConstraint(version)), ImmutableListMultimap.of(fromConf, toConf)))
    }

    def exclude(String name, List<String> confs = []) {
        def exclude = new DefaultExclude(DefaultModuleIdentifier.newId("group", name), confs as String[], "exact")
        excludes.add(exclude)
        exclude
    }

    def artifact(String name, List<String> confs = []) {
        artifacts.add(new Artifact(new DefaultIvyArtifactName(name, "type", "ext", "classifier"), confs as Set<String>))
    }

}
