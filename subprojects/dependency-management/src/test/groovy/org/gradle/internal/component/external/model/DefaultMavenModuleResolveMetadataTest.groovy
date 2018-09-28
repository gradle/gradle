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

import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.attributes.Usage
import org.gradle.api.internal.artifacts.DefaultImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.dependencies.DefaultMutableVersionConstraint
import org.gradle.api.internal.artifacts.dsl.dependencies.PlatformSupport
import org.gradle.api.internal.artifacts.repositories.metadata.DefaultMavenImmutableAttributesFactory
import org.gradle.api.internal.artifacts.repositories.metadata.MavenMutableModuleMetadataFactory
import org.gradle.api.internal.model.NamedObjectInstantiator
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.internal.component.external.descriptor.Configuration
import org.gradle.internal.component.external.descriptor.MavenScope
import org.gradle.internal.component.external.model.maven.DefaultMutableMavenModuleResolveMetadata
import org.gradle.internal.component.external.model.maven.MavenDependencyDescriptor
import org.gradle.internal.component.external.model.maven.MavenDependencyType
import org.gradle.internal.component.model.ModuleSource
import org.gradle.util.TestUtil
import spock.lang.Unroll

import static org.gradle.internal.component.external.model.DefaultModuleComponentSelector.newSelector

class DefaultMavenModuleResolveMetadataTest extends AbstractLazyModuleComponentResolveMetadataTest {

    private
    final mavenMetadataFactory = new MavenMutableModuleMetadataFactory(new DefaultImmutableModuleIdentifierFactory(), TestUtil.attributesFactory(), TestUtil.objectInstantiator(), TestUtil.featurePreviews())

    @Override
    ModuleComponentResolveMetadata createMetadata(ModuleComponentIdentifier id, List<Configuration> configurations, List dependencies) {
        mavenMetadataFactory.create(id, dependencies).asImmutable()
    }

    def "builds and caches dependencies for a scope"() {
        given:
        configuration("compile")
        configuration("runtime", ["compile"])
        dependency("org", "module", "1.1", "Compile")
        dependency("org", "module", "1.2", "Runtime")
        dependency("org", "module", "1.3", "Test")
        dependency("org", "module", "1.4", "System")

        when:
        def md = metadata
        def runtime = md.getConfiguration("runtime")
        def compile = md.getConfiguration("compile")

        then:
        runtime.dependencies*.selector*.version == ["1.1", "1.2"]
        runtime.dependencies.is(runtime.dependencies)

        compile.dependencies*.selector*.version == ["1.1"]
        compile.dependencies.is(compile.dependencies)
    }

    def "builds and caches artifacts for a configuration"() {
        when:
        def runtime = metadata.getConfiguration("runtime")

        then:
        runtime.artifacts*.name.name == ["module"]
        runtime.artifacts*.name.extension == ["jar"]
        runtime.artifacts.is(runtime.artifacts)
    }

    def "each configuration contains a single variant containing the status attribute and the artifacts of the configuration"() {
        when:
        def runtime = metadata.getConfiguration("runtime")

        then:
        runtime.variants.size() == 1
        def firstVariant = runtime.variants.first()
        assertHasOnlyStatusAttribute(firstVariant.attributes)
        firstVariant.artifacts == runtime.artifacts
    }

    def "artifacts include union of those inherited from other configurations"() {
        when:
        def compileArtifacts = metadata.getConfiguration("compile").artifacts
        def runtimeArtifacts = metadata.getConfiguration("runtime").artifacts
        def defaultArtifacts = metadata.getConfiguration("default").artifacts

        then:
        runtimeArtifacts.size() == compileArtifacts.size()
        defaultArtifacts.size() == runtimeArtifacts.size()
    }

    def "copy with different source"() {
        given:
        def source = Stub(ModuleSource)
        def mutable = mavenMetadataFactory.create(id)
        mutable.packaging = "other"
        mutable.relocated = true
        mutable.snapshotTimestamp = "123"
        def metadata = mutable.asImmutable()

        when:
        def copy = metadata.withSource(source)

        then:
        copy.source == source
        copy.packaging == "other"
        copy.relocated
        copy.snapshotTimestamp == "123"
    }

    def "recognises pom packaging"() {
        when:
        def metadata = mavenMetadataFactory.create(id)
        metadata.packaging = packaging

        then:
        metadata.packaging == packaging
        metadata.pomPackaging == isPom
        metadata.knownJarPackaging == isJar

        where:
        packaging      | isPom | isJar
        "pom"          | true  | false
        "jar"          | false | true
        "war"          | false | false
        "maven-plugin" | false | true
    }

    @Unroll
    def "recognises java library for packaging=#packaging"() {
        given:
        def stringUsageAttribute = Attribute.of(Usage.USAGE_ATTRIBUTE.getName(), String.class)
        def componentTypeAttribute = PlatformSupport.COMPONENT_CATEGORY
        def metadata = new DefaultMutableMavenModuleResolveMetadata(Mock(ModuleVersionIdentifier), id, [], new DefaultMavenImmutableAttributesFactory(TestUtil.attributesFactory(), NamedObjectInstantiator.INSTANCE), TestUtil.objectInstantiator())
        metadata.packaging = packaging
        metadata.variantMetadataRules.variantDerivationStrategy = new JavaEcosystemVariantDerivationStrategy()

        when:
        def immutableMetadata = metadata.asImmutable()
        def variantsForGraphTraversal = immutableMetadata.getVariantsForGraphTraversal().orNull()
        def compileConf = immutableMetadata.getConfiguration("compile")
        def runtimeConf = immutableMetadata.getConfiguration("runtime")

        then:
        assertHasOnlyStatusAttribute(compileConf.attributes)
        assertHasOnlyStatusAttribute(runtimeConf.attributes)

        variantsForGraphTraversal.size() == 6
        variantsForGraphTraversal[0].name == "compile"
        variantsForGraphTraversal[0].attributes.getAttribute(stringUsageAttribute) == "java-api"
        variantsForGraphTraversal[1].name == "runtime"
        variantsForGraphTraversal[1].attributes.getAttribute(stringUsageAttribute) == "java-runtime"
        variantsForGraphTraversal[2].name == "platform-compile"
        variantsForGraphTraversal[2].attributes.getAttribute(stringUsageAttribute) == "java-api"
        variantsForGraphTraversal[2].attributes.getAttribute(componentTypeAttribute) == "platform"
        variantsForGraphTraversal[3].name == "platform-runtime"
        variantsForGraphTraversal[3].attributes.getAttribute(stringUsageAttribute) == "java-runtime"
        variantsForGraphTraversal[3].attributes.getAttribute(componentTypeAttribute) == "platform"
        variantsForGraphTraversal[4].name == "enforced-platform-compile"
        variantsForGraphTraversal[4].attributes.getAttribute(stringUsageAttribute) == "java-api"
        variantsForGraphTraversal[4].attributes.getAttribute(componentTypeAttribute) == "enforced-platform"
        variantsForGraphTraversal[5].name == "enforced-platform-runtime"
        variantsForGraphTraversal[5].attributes.getAttribute(stringUsageAttribute) == "java-runtime"
        variantsForGraphTraversal[5].attributes.getAttribute(componentTypeAttribute) == "enforced-platform"

        where:
        packaging << ["pom", "jar", "maven-plugin", "war", "aar"]
    }

    def dependency(String org, String module, String version, String scope) {
        def selector = newSelector(DefaultModuleIdentifier.newId(org, module), new DefaultMutableVersionConstraint(version))
        dependencies.add(new MavenDependencyDescriptor(MavenScope.valueOf(scope), MavenDependencyType.DEPENDENCY, selector, null, []))
    }

    private void assertHasOnlyStatusAttribute(AttributeContainer attributes) {
        assert attributes.keySet().size() == 1
        assert attributes.getAttribute(ProjectInternal.STATUS_ATTRIBUTE) == 'integration'
    }

}
