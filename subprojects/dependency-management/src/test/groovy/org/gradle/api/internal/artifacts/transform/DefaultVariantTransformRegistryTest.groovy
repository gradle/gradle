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

package org.gradle.api.internal.artifacts.transform

import org.gradle.api.artifacts.transform.ArtifactTransform
import org.gradle.api.artifacts.transform.ArtifactTransformAction
import org.gradle.api.artifacts.transform.ArtifactTransformOutputs
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.VariantTransformConfigurationException
import org.gradle.api.attributes.Attribute
import org.gradle.api.internal.DynamicObjectAware
import org.gradle.api.internal.tasks.properties.PropertyWalker
import org.gradle.api.plugins.ExtensionAware
import org.gradle.internal.classloader.ClassLoaderHierarchyHasher
import org.gradle.internal.hash.HashCode
import org.gradle.internal.isolation.TestIsolatableFactory
import org.gradle.internal.snapshot.ValueSnapshotter
import org.gradle.internal.service.ServiceRegistry
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.AttributeTestUtil
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Specification

import javax.inject.Inject

class DefaultVariantTransformRegistryTest extends Specification {
    public static final TEST_ATTRIBUTE = Attribute.of("TEST", String)

    @Rule
    final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()

    def instantiatorFactory = TestUtil.instantiatorFactory()
    def transformerInvoker = Mock(TransformerInvoker)
    def valueSnapshotter = Mock(ValueSnapshotter)
    def propertyWalker = Mock(PropertyWalker)
    def isolatableFactory = new TestIsolatableFactory()
    def classLoaderHierarchyHasher = Mock(ClassLoaderHierarchyHasher)
    def attributesFactory = AttributeTestUtil.attributesFactory()
    def registry = new DefaultVariantTransformRegistry(instantiatorFactory, attributesFactory, isolatableFactory, classLoaderHierarchyHasher, Stub(ServiceRegistry), transformerInvoker, valueSnapshotter, propertyWalker)

    def "setup"() {
        _ * classLoaderHierarchyHasher.getClassLoaderHash(_) >> HashCode.fromInt(123)
    }

    def "creates registration without parameters"() {
        when:
        registry.registerTransform {
            it.from.attribute(TEST_ATTRIBUTE, "FROM")
            it.to.attribute(TEST_ATTRIBUTE, "TO")
            it.artifactTransform(TestArtifactTransform)
        }

        then:
        registry.transforms.size() == 1
        def registration = registry.transforms[0]
        registration.from.getAttribute(TEST_ATTRIBUTE) == "FROM"
        registration.to.getAttribute(TEST_ATTRIBUTE) == "TO"
        registration.transformationStep.transformer.implementationClass == TestArtifactTransform
        registration.transformationStep.transformer.parameters.isolate() == []
    }

    def "creates registration with parameters"() {
        when:
        registry.registerTransform {
            it.from.attribute(TEST_ATTRIBUTE, "FROM")
            it.to.attribute(TEST_ATTRIBUTE, "TO")
            it.artifactTransform(TestArtifactTransformWithParams) { artifactConfig ->
                artifactConfig.params("EXTRA_1", "EXTRA_2")
            }
        }

        then:
        registry.transforms.size() == 1
        def registration = registry.transforms[0]
        registration.from.getAttribute(TEST_ATTRIBUTE) == "FROM"
        registration.to.getAttribute(TEST_ATTRIBUTE) == "TO"
        registration.transformationStep.transformer.implementationClass == TestArtifactTransformWithParams
        registration.transformationStep.transformer.parameters.isolate() == ["EXTRA_1", "EXTRA_2"]
    }

    def "delegates are DSL decorated but not extensible when registering without config object"() {
        def registration
        def config

        when:
        registry.registerTransform {
            registration = it
            it.from.attribute(TEST_ATTRIBUTE, "FROM")
            it.to.attribute(TEST_ATTRIBUTE, "TO")
            it.artifactTransform(TestArtifactTransform) {
                config = it
            }
        }

        then:
        registration instanceof DynamicObjectAware
        !(registration instanceof ExtensionAware)
        config instanceof DynamicObjectAware
        !(config instanceof ExtensionAware)
    }

    def "creates registration with annotated config object and without parameters"() {
        when:
        registry.registerTransform(TestTransformConfig) {
            it.from.attribute(TEST_ATTRIBUTE, "FROM")
            it.to.attribute(TEST_ATTRIBUTE, "TO")
            assert it.actionClass == TestArtifactTransformAction
        }

        then:
        registry.transforms.size() == 1
        def registration = registry.transforms[0]
        registration.from.getAttribute(TEST_ATTRIBUTE) == "FROM"
        registration.to.getAttribute(TEST_ATTRIBUTE) == "TO"
        registration.transformationStep.transformer.implementationClass == TestArtifactTransformAction
        registration.transformationStep.transformer.parameterObject.isolate() instanceof TestTransformConfig
    }

    def "creates registration with unannotated config object and without parameters"() {
        when:
        registry.registerTransform(UnAnnotatedTestTransformConfig) {
            it.from.attribute(TEST_ATTRIBUTE, "FROM")
            it.to.attribute(TEST_ATTRIBUTE, "TO")
            it.actionClass = TestArtifactTransformAction
        }

        then:
        registry.transforms.size() == 1
        def registration = registry.transforms[0]
        registration.from.getAttribute(TEST_ATTRIBUTE) == "FROM"
        registration.to.getAttribute(TEST_ATTRIBUTE) == "TO"
        registration.transformationStep.transformer.implementationClass == TestArtifactTransformAction
        registration.transformationStep.transformer.parameterObject.isolate() instanceof UnAnnotatedTestTransformConfig
    }

    def "delegates are DSL decorated but not extensible when registering with config object"() {
        def registration

        when:
        registry.registerTransform(TestTransformConfig) {
            it.from.attribute(TEST_ATTRIBUTE, "FROM")
            it.to.attribute(TEST_ATTRIBUTE, "TO")
            it.actionClass = TestArtifactTransformAction
            registration = it
        }

        then:
        registration instanceof DynamicObjectAware
        !(registration instanceof ExtensionAware)
    }

    def "fails when artifactTransform configuration action fails for registration"() {
        def failure = new RuntimeException("Bad config")

        when:
        registry.registerTransform {
            it.from.attribute(TEST_ATTRIBUTE, "from")
            it.to.attribute(TEST_ATTRIBUTE, "to")
            it.artifactTransform(TestArtifactTransformAction) { artifactConfig ->
                throw failure
            }
        }

        then:
        def e = thrown(RuntimeException)
        e == failure
    }

    def "fails when no artifactTransform provided for registration"() {
        when:
        registry.registerTransform {
        }

        then:
        def e = thrown(VariantTransformConfigurationException)
        e.message == 'Could not register transform: an ArtifactTransform must be provided.'
        e.cause == null
    }

    def "fails when multiple artifactTransforms are provided for registration"() {
        when:
        registry.registerTransform {
            it.artifactTransform(TestArtifactTransformAction)
            it.artifactTransform(TestArtifactTransformAction)
        }

        then:
        def e = thrown(VariantTransformConfigurationException)
        e.message == 'Could not register transform: only one ArtifactTransform may be provided for registration.'
        e.cause == null
    }

    def "fails when no from attributes are provided for registration"() {
        when:
        registry.registerTransform {
            it.to.attribute(TEST_ATTRIBUTE, "to")
            it.artifactTransform(TestArtifactTransformAction)
        }

        then:
        def e = thrown(VariantTransformConfigurationException)
        e.message == "Could not register transform: at least one 'from' attribute must be provided."
        e.cause == null
    }

    def "fails when no to attributes are provided for registration"() {
        when:
        registry.registerTransform {
            it.from.attribute(TEST_ATTRIBUTE, "from")
            it.artifactTransform(TestArtifactTransformAction)
        }

        then:
        def e = thrown(VariantTransformConfigurationException)
        e.message == "Could not register transform: at least one 'to' attribute must be provided."
        e.cause == null
    }

    def "fails when to attributes are not a subset of from attributes"() {
        when:
        registry.registerTransform {
            it.from.attribute(TEST_ATTRIBUTE, "from")
            it.from.attribute(Attribute.of("from2", String), "from")
            it.to.attribute(TEST_ATTRIBUTE, "to")
            it.to.attribute(Attribute.of("other", Integer), 12)
            it.artifactTransform(TestArtifactTransformAction)
        }

        then:
        def e = thrown(VariantTransformConfigurationException)
        e.message == "Could not register transform: each 'to' attribute must be included as a 'from' attribute."
        e.cause == null
    }

    @TransformAction(TestArtifactTransformAction)
    static class TestTransformConfig {
        String value
    }

    static class UnAnnotatedTestTransformConfig {
        String value
    }

    static class TestArtifactTransform extends ArtifactTransform {
        @Override
        List<File> transform(File input) {
            throw new UnsupportedOperationException()
        }
    }

    static class TestArtifactTransformAction implements ArtifactTransformAction {
        @Override
        void transform(ArtifactTransformOutputs outputs) {
        }
    }

    static class TestArtifactTransformWithParams extends ArtifactTransform {
        @Inject
        TestArtifactTransformWithParams(String extra1, String extra2) {
        }

        @Override
        List<File> transform(File input) {
            throw new UnsupportedOperationException()
        }
    }
}
