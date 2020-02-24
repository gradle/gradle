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

import com.google.common.collect.ImmutableSet
import org.gradle.api.artifacts.transform.ArtifactTransform
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.InputArtifactDependencies
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.artifacts.transform.VariantTransformConfigurationException
import org.gradle.api.attributes.Attribute
import org.gradle.api.internal.DomainObjectContext
import org.gradle.api.internal.DynamicObjectAware
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.file.FileLookup
import org.gradle.api.internal.project.ProjectStateRegistry
import org.gradle.api.internal.tasks.properties.InspectionScheme
import org.gradle.api.internal.tasks.properties.PropertyWalker
import org.gradle.api.plugins.ExtensionAware
import org.gradle.internal.fingerprint.FileCollectionFingerprinterRegistry
import org.gradle.internal.hash.ClassLoaderHierarchyHasher
import org.gradle.internal.hash.HashCode
import org.gradle.internal.isolation.TestIsolatableFactory
import org.gradle.internal.operations.TestBuildOperationExecutor
import org.gradle.internal.service.ServiceLookup
import org.gradle.internal.service.ServiceRegistry
import org.gradle.internal.snapshot.ValueSnapshotter
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
    def transformerInvocationFactory = Mock(TransformerInvocationFactory)
    def valueSnapshotter = Mock(ValueSnapshotter)
    def fileCollectionFingerprinterRegistry = Mock(FileCollectionFingerprinterRegistry)
    def fileCollectionFactory = Mock(FileCollectionFactory)
    def propertyWalker = Mock(PropertyWalker)
    def inspectionScheme = Stub(InspectionScheme) {
        getPropertyWalker() >> propertyWalker
    }
    def isolatableFactory = new TestIsolatableFactory()
    def classLoaderHierarchyHasher = Mock(ClassLoaderHierarchyHasher)
    def attributesFactory = AttributeTestUtil.attributesFactory()
    def registryFactory = new DefaultTransformationRegistrationFactory(
        new TestBuildOperationExecutor(),
        isolatableFactory,
        classLoaderHierarchyHasher,
        transformerInvocationFactory,
        valueSnapshotter,
        fileCollectionFactory,
        Mock(FileLookup),
        fileCollectionFingerprinterRegistry,
        Mock(DomainObjectContext),
        Mock(ProjectStateRegistry),
        new ArtifactTransformParameterScheme(
            instantiatorFactory.injectScheme(),
            inspectionScheme
        ),
        new ArtifactTransformActionScheme(
            instantiatorFactory.injectScheme(
                ImmutableSet.of(InputArtifact, InputArtifactDependencies)
            ),
            inspectionScheme,
            instantiatorFactory.injectScheme()
        ),
        Stub(ServiceLookup)
    )
    def registry = new DefaultVariantTransformRegistry(instantiatorFactory, attributesFactory, Stub(ServiceRegistry), registryFactory, instantiatorFactory.injectScheme())

    def "setup"() {
        _ * classLoaderHierarchyHasher.getClassLoaderHash(_) >> HashCode.fromInt(123)
    }

    def "creates legacy registration without parameters"() {
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
        registration.transformationStep.transformer.isolatableParameters.isolate() == []
    }

    def "creates legacy registration with parameters"() {
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
        registration.transformationStep.transformer.isolatableParameters.isolate() == ["EXTRA_1", "EXTRA_2"]
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

    def "creates registration with annotated parameters object"() {
        when:
        registry.registerTransform(TestTransform) {
            it.from.attribute(TEST_ATTRIBUTE, "FROM")
            it.to.attribute(TEST_ATTRIBUTE, "TO")
        }

        then:
        registry.transforms.size() == 1
        def registration = registry.transforms[0]
        registration.from.getAttribute(TEST_ATTRIBUTE) == "FROM"
        registration.to.getAttribute(TEST_ATTRIBUTE) == "TO"
        registration.transformationStep.transformer.implementationClass == TestTransform
        registration.transformationStep.transformer.parameterObject instanceof TestTransform.Parameters
    }

    def "creates registration for parametereless action"() {
        when:
        registry.registerTransform(ParameterlessTestTransform) {
            it.from.attribute(TEST_ATTRIBUTE, "FROM")
            it.to.attribute(TEST_ATTRIBUTE, "TO")
        }

        then:
        registry.transforms.size() == 1
        def registration = registry.transforms[0]
        registration.from.getAttribute(TEST_ATTRIBUTE) == "FROM"
        registration.to.getAttribute(TEST_ATTRIBUTE) == "TO"
        registration.transformationStep.transformer.implementationClass == ParameterlessTestTransform
        registration.transformationStep.transformer.parameterObject == null
    }

    def "cannot use TransformParameters as parameter type"() {
        when:
        registry.registerTransform(UnspecifiedTestTransform) {
            it.from.attribute(TEST_ATTRIBUTE, "FROM")
            it.to.attribute(TEST_ATTRIBUTE, "TO")
        }

        then:
        def e = thrown(VariantTransformConfigurationException)
        e.message == 'Could not register artifact transform DefaultVariantTransformRegistryTest.UnspecifiedTestTransform.'
        e.cause.message == 'Could not create the parameters for DefaultVariantTransformRegistryTest.UnspecifiedTestTransform: must use a sub-type of TransformParameters as the parameters type. Use TransformParameters.None as the parameters type for implementations that do not take parameters.'
    }

    def "cannot configure parameters for parameterless action"() {
        when:
        registry.registerTransform(ParameterlessTestTransform) {
            it.from.attribute(TEST_ATTRIBUTE, "FROM")
            it.to.attribute(TEST_ATTRIBUTE, "TO")
            it.parameters {
            }
        }

        then:
        def e = thrown(VariantTransformConfigurationException)
        e.message == 'Could not register artifact transform DefaultVariantTransformRegistryTest.ParameterlessTestTransform.'
        e.cause.message == 'Cannot configure parameters for artifact transform without parameters.'
    }

    def "cannot query parameters object for parameterless action"() {
        when:
        registry.registerTransform(ParameterlessTestTransform) {
            it.from.attribute(TEST_ATTRIBUTE, "FROM")
            it.to.attribute(TEST_ATTRIBUTE, "TO")
            it.parameters
        }

        then:
        def e = thrown(VariantTransformConfigurationException)
        e.message == 'Could not register artifact transform DefaultVariantTransformRegistryTest.ParameterlessTestTransform.'
        e.cause.message == 'Cannot query parameters for artifact transform without parameters.'
    }

    def "delegates are DSL decorated but not extensible when registering with config object"() {
        def registration

        when:
        registry.registerTransform(TestTransform) {
            it.from.attribute(TEST_ATTRIBUTE, "FROM")
            it.to.attribute(TEST_ATTRIBUTE, "TO")
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
            it.artifactTransform(TestArtifactTransform) { artifactConfig ->
                throw failure
            }
        }

        then:
        def e = thrown(VariantTransformConfigurationException)
        e.message == 'Could not register artifact transform DefaultVariantTransformRegistryTest.TestArtifactTransform (from {TEST=from} to {TEST=to}).'
        e.cause == failure
    }

    def "fails when no artifactTransform provided for registration"() {
        when:
        registry.registerTransform {
        }

        then:
        def e = thrown(VariantTransformConfigurationException)
        e.message == 'Could not register artifact transform.'
        e.cause.message == 'An artifact transform action type must be provided.'
    }

    def "fails when multiple artifactTransforms are provided for registration"() {
        when:
        registry.registerTransform {
            it.artifactTransform(TestArtifactTransform)
            it.artifactTransform(TestArtifactTransform)
        }

        then:
        def e = thrown(VariantTransformConfigurationException)
        e.message == 'Could not register artifact transform.'
        e.cause.message == 'Only one ArtifactTransform may be provided for registration.'
    }

    def "fails when no from attributes are provided for legacy registration"() {
        when:
        registry.registerTransform {
            it.to.attribute(TEST_ATTRIBUTE, "to")
            it.artifactTransform(TestArtifactTransform)
        }

        then:
        def e = thrown(VariantTransformConfigurationException)
        e.message == "Could not register artifact transform DefaultVariantTransformRegistryTest.TestArtifactTransform (from {} to {TEST=to})."
        e.cause.message == "At least one 'from' attribute must be provided."
    }

    def "fails when no from attributes are provided for registerTransform"() {
        when:
        registry.registerTransform(TestTransform) {
            it.to.attribute(TEST_ATTRIBUTE, "to")
        }

        then:
        def e = thrown(VariantTransformConfigurationException)
        e.message == "Could not register artifact transform DefaultVariantTransformRegistryTest.TestTransform (from {} to {TEST=to})."
        e.cause.message == "At least one 'from' attribute must be provided."
    }

    def "fails when no to attributes are provided for legacy registration"() {
        when:
        registry.registerTransform {
            it.from.attribute(TEST_ATTRIBUTE, "from")
            it.artifactTransform(TestArtifactTransform)
        }

        then:
        def e = thrown(VariantTransformConfigurationException)
        e.message == "Could not register artifact transform DefaultVariantTransformRegistryTest.TestArtifactTransform (from {TEST=from} to {})."
        e.cause.message == "At least one 'to' attribute must be provided."
    }

    def "fails when no to attributes are provided for registerTransform"() {
        when:
        registry.registerTransform(TestTransform) {
            it.from.attribute(TEST_ATTRIBUTE, "from")
        }

        then:
        def e = thrown(VariantTransformConfigurationException)
        e.message == "Could not register artifact transform DefaultVariantTransformRegistryTest.TestTransform (from {TEST=from} to {})."
        e.cause.message == "At least one 'to' attribute must be provided."
    }

    def "fails when to attributes are not a subset of from attributes for legacy registration"() {
        when:
        registry.registerTransform {
            it.from.attribute(TEST_ATTRIBUTE, "from")
            it.from.attribute(Attribute.of("from2", String), "from")
            it.to.attribute(TEST_ATTRIBUTE, "to")
            it.to.attribute(Attribute.of("other", Integer), 12)
            it.artifactTransform(TestArtifactTransform)
        }

        then:
        def e = thrown(VariantTransformConfigurationException)
        e.message == "Could not register artifact transform DefaultVariantTransformRegistryTest.TestArtifactTransform (from {TEST=from, from2=from} to {TEST=to, other=12})."
        e.cause.message == "Each 'to' attribute must be included as a 'from' attribute."
    }

    def "fails when to attributes are not a subset of from attributes for registerTransform"() {
        when:
        registry.registerTransform(TestTransform) {
            it.from.attribute(TEST_ATTRIBUTE, "from")
            it.from.attribute(Attribute.of("from2", String), "from")
            it.to.attribute(TEST_ATTRIBUTE, "to")
            it.to.attribute(Attribute.of("other", Integer), 12)
        }

        then:
        def e = thrown(VariantTransformConfigurationException)
        e.message == "Could not register artifact transform DefaultVariantTransformRegistryTest.TestTransform (from {TEST=from, from2=from} to {TEST=to, other=12})."
        e.cause.message == "Each 'to' attribute must be included as a 'from' attribute."
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

    static abstract class TestTransform implements TransformAction<Parameters> {
        static class Parameters implements TransformParameters {
            String value
        }

        @Override
        void transform(TransformOutputs outputs) {
        }
    }

    static abstract class ParameterlessTestTransform implements TransformAction<TransformParameters.None> {
        @Override
        void transform(TransformOutputs outputs) {
        }
    }

    static abstract class UnspecifiedTestTransform implements TransformAction<TransformParameters> {
        @Override
        void transform(TransformOutputs outputs) {
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
