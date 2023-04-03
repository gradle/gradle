/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.jvm.component.internal

import org.gradle.api.GradleException
import org.gradle.api.NamedDomainObjectSet
import org.gradle.api.artifacts.ConfigurationPublications
import org.gradle.api.artifacts.ConfigurationVariant
import org.gradle.api.artifacts.PublishArtifactSet
import org.gradle.api.attributes.Usage
import org.gradle.api.capabilities.CapabilitiesMetadata
import org.gradle.api.capabilities.Capability
import org.gradle.api.component.ComponentFeature
import org.gradle.api.component.ConfigurationBackedConsumableVariant
import org.gradle.api.component.ConsumableVariant
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal
import org.gradle.api.internal.component.MavenPublishingAwareVariant
import org.gradle.api.internal.java.usagecontext.FeatureConfigurationVariant
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.internal.component.external.model.DefaultImmutableCapability
import org.gradle.internal.component.external.model.ImmutableCapabilities
import org.gradle.jvm.component.JvmFeature
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.util.AttributeTestUtil
import org.gradle.util.TestUtil

import javax.inject.Inject

/**
 * Tests {@link DefaultJvmSoftwareComponent}.
 */
class DefaultJvmSoftwareComponentTest extends AbstractProjectBuilderSpec {

    def sourceSets

    def setup() {
        project.plugins.apply(JavaBasePlugin)
        sourceSets = project.getExtensions().getByType(JavaPluginExtension.class).getSourceSets()
    }

    def "has no variants by default"() {
        given:
        def component = project.objects.newInstance(DefaultJvmSoftwareComponent, "name")

        expect:
        component.variants.isEmpty()
    }

    def "can create multiple component instances"() {
        expect:
        project.objects.newInstance(DefaultJvmSoftwareComponent, "name1")
        project.objects.newInstance(DefaultJvmSoftwareComponent, "name2")
        project.objects.newInstance(DefaultJvmSoftwareComponent, "name3")
    }

    def "can add features"() {
        given:
        def component = project.objects.newInstance(DefaultJvmSoftwareComponent, "name")

        when:
        def feature = new DefaultTestFeature("test")
        component.features.add(feature)

        then:
        component.features == [feature] as Set

        when:
        def feature2 = new DefaultTestFeature("test2")
        component.features.add(feature2)

        then:
        component.features == [feature, feature2] as Set
    }

    def "aggregates variants from features"() {
        given:
        def component = project.objects.newInstance(DefaultJvmSoftwareComponent, "name")

        when:
        def feature = new DefaultTestFeature("test")
        component.features.add(feature)

        then:
        component.variants == [] as Set

        when:
        def variant1 = newVariant("variant1")
        feature.variants.add(variant1)

        then:
        component.variants == [variant1] as Set

        when:
        def feature2 = new DefaultTestFeature("test2")
        component.features.add(feature2)

        then:
        component.variants == [variant1] as Set

        when:
        def variant2 = newVariant("variant2")
        feature2.variants.add(variant2)

        then:
        component.variants == [variant1, variant2] as Set

        when:
        def variant3 = newVariant("variant3")
        feature.variants.add(variant3)

        then:
        component.variants == [variant1, variant2, variant3] as Set
    }

    def "can register feature implementations and create instances"() {
        given:
        def component = project.objects.newInstance(DefaultJvmSoftwareComponent, "name")
        component.features.registerBinding(TestFeature, DefaultTestFeature)

        when:
        def feature = component.features.create("test", TestFeature)

        then:
        feature instanceof DefaultTestFeature
        component.features.test == feature

        when:
        feature.variants.add(newVariant("variant1"))

        then:
        component.variants == [feature.variants.variant1] as Set
    }

    def "can create multiple component instances with different features"() {
        when:
        def comp1 = project.objects.newInstance(DefaultJvmSoftwareComponent, "name1")
        comp1.features.registerBinding(TestFeature, DefaultTestFeature)
        def feat1 = comp1.getFeatures().create("feat1", TestFeature)

        def comp2 = project.objects.newInstance(DefaultJvmSoftwareComponent, "name2")
        comp2.features.registerBinding(TestFeature, DefaultTestFeature)
        def feat2 = comp2.getFeatures().create("feat2", TestFeature)

        then:
        feat1 != feat2
        comp1.features.feat1 == feat1
        comp2.features.feat2 == feat2
    }

    def "can instantiate JvmFeature features"() {
        given:
        project.group = "org"
        def component = project.objects.newInstance(DefaultJvmSoftwareComponent, "name")

        when:
        component.features.create("test", JvmFeature)

        then:
        component.features.test instanceof JvmFeature
        component.features.test.sourceSet == sourceSets.getByName('test')
        component.features.test.commonCapabilities.capabilities.collect { it.group + ":" + it.name } == ["org:test-project-test"]
    }

    def "cannot create multiple JvmFeature instances with the same name"() {
        when:
        def comp1 = project.objects.newInstance(DefaultJvmSoftwareComponent, "name")
        comp1.features.create("feature", JvmFeature)
        def comp2 = project.objects.newInstance(DefaultJvmSoftwareComponent, "name2")
        comp2.features.create("feature", JvmFeature)

        then:
        def e = thrown(GradleException)
        e.message == "Cannot create JvmFeature since source set 'feature' already exists."
    }

    def "variants are mapped to usage contexts"() {
        given:
        def capability = new DefaultImmutableCapability("org", "foo", null)

        when:
        def component = project.objects.newInstance(DefaultJvmSoftwareComponent, "name")
        component.features.registerBinding(TestFeature, DefaultTestFeature)

        def feature = component.features.create("main", TestFeature)
        feature.variants.add(configurationVariant("variant1", [], false))
        feature.variants.add(configurationVariant("variant2", [], true))
        feature.variants.add(configurationVariant("variant3", [capability], false))
        feature.variants.add(configurationVariant("variant4", [capability], true))

        then:
        component.usages.size() == 4

        def usage1 = component.usages.find { it.name == "variant1" } as FeatureConfigurationVariant
        def usage2 = component.usages.find { it.name == "variant2" } as FeatureConfigurationVariant
        def usage3 = component.usages.find { it.name == "variant3" } as FeatureConfigurationVariant
        def usage4 = component.usages.find { it.name == "variant4" } as FeatureConfigurationVariant

        [usage1, usage2, usage3, usage4].collect { it.optional } == [false, false, true, true]
        [usage1, usage2, usage3, usage4].collect { it.scopeMapping } == [
            MavenPublishingAwareVariant.ScopeMapping.runtime,
            MavenPublishingAwareVariant.ScopeMapping.compile,
            MavenPublishingAwareVariant.ScopeMapping.runtime_optional,
            MavenPublishingAwareVariant.ScopeMapping.compile_optional,
        ]
    }

    ConsumableVariant newVariant(String name) {
        return Mock(ConsumableVariant) {
            getName() >> name
        }
    }

    ConfigurationBackedConsumableVariant configurationVariant(String name, Collection<Capability> capabilities, boolean api) {

        def usage = api ? Usage.JAVA_API : Usage.JAVA_RUNTIME
        def attributes = AttributeTestUtil.attributesFactory().of(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage, usage))

        return Mock(ConfigurationBackedConsumableVariant) {
            getName() >> name
            getCapabilities() >> ImmutableCapabilities.of(capabilities)
            getAttributes() >> attributes
            getConfiguration() >> Mock(ConfigurationInternal) {
                getName() >> name
                getArtifacts() >> Mock(PublishArtifactSet) {
                    iterator() >> Collections.emptyIterator()
                }
                getAttributes() >> attributes
                getOutgoing() >> Mock(ConfigurationPublications) {
                    getVariants() >> TestUtil.domainObjectCollectionFactory().newNamedDomainObjectContainer(ConfigurationVariant)
                }
            }
        }
    }

    interface TestFeature extends ComponentFeature { }

    static class DefaultTestFeature implements TestFeature {

        private final String name
        private final NamedDomainObjectSet<ConsumableVariant> variants =
            TestUtil.domainObjectCollectionFactory().newNamedDomainObjectContainer(ConsumableVariant)

        @Inject
        DefaultTestFeature(String name) {
            this.name = name
        }

        @Override
        String getDescription() {
            return null
        }

        @Override
        String getName() {
            return name
        }

        @Override
        CapabilitiesMetadata getCommonCapabilities() {
            return ImmutableCapabilities.of(new DefaultImmutableCapability("org", name, null))
        }

        @Override
        NamedDomainObjectSet<? extends ConsumableVariant> getVariants() {
            return variants
        }
    }
}
