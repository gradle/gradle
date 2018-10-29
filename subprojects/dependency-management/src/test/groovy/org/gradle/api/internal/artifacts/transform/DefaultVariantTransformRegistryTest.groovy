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
import org.gradle.api.artifacts.transform.TransformInvocationException
import org.gradle.api.artifacts.transform.VariantTransformConfigurationException
import org.gradle.api.attributes.Attribute
import org.gradle.api.reflect.ObjectInstantiationException
import org.gradle.internal.classloader.ClassLoaderHierarchyHasher
import org.gradle.internal.hash.HashCode
import org.gradle.internal.isolation.IsolatableFactory
import org.gradle.internal.snapshot.ValueSnapshot
import org.gradle.internal.snapshot.impl.ArrayValueSnapshot
import org.gradle.internal.snapshot.impl.StringValueSnapshot
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.AttributeTestUtil
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Specification

import javax.inject.Inject

class DefaultVariantTransformRegistryTest extends Specification {
    public static final TEST_ATTRIBUTE = Attribute.of("TEST", String)
    public static final TEST_INPUT = new File("input").absoluteFile

    @Rule
    final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()

    def instantiatorFactory = TestUtil.instantiatorFactory()
    def outputDirectory = tmpDir.createDir("OUTPUT_DIR")
    def outputFile = outputDirectory.file('input/OUTPUT_FILE')
    def transformedFileCache = Mock(CachingTransformerExecutor)
    def isolatableFactory = Mock(IsolatableFactory)
    def classLoaderHierarchyHasher = Mock(ClassLoaderHierarchyHasher)
    def attributesFactory = AttributeTestUtil.attributesFactory()
    def transformerInvoker = new DefaultTransformerInvoker(transformedFileCache)
    def registry = new DefaultVariantTransformRegistry(instantiatorFactory, attributesFactory, isolatableFactory, classLoaderHierarchyHasher, transformerInvoker)

    def "creates registration without configuration"() {
        given:
        def valueSnapshotArray = [] as ValueSnapshot[]

        when:
        registry.registerTransform {
            it.from.attribute(TEST_ATTRIBUTE, "FROM")
            it.to.attribute(TEST_ATTRIBUTE, "TO")
            it.artifactTransform(TestArtifactTransform)
        }

        then:
        1 * isolatableFactory.isolate([] as Object[]) >> new ArrayValueSnapshot(valueSnapshotArray)
        1 * classLoaderHierarchyHasher.getClassLoaderHash(TestArtifactTransform.classLoader) >> HashCode.fromInt(123)

        and:
        registry.transforms.size() == 1
        def registration = registry.transforms[0]
        registration.from.getAttribute(TEST_ATTRIBUTE) == "FROM"
        registration.to.getAttribute(TEST_ATTRIBUTE) == "TO"

        and:
        !outputFile.exists()

        when:
        def transformed = registration.transformationStep.transform(TransformationSubject.initial(TEST_INPUT)).files

        then:
        transformed.size() == 1
        transformed.first() == new File(outputDirectory, "OUTPUT_FILE")

        and:
        interaction {
            runTransformer(TEST_INPUT)
        }
    }

    def "creates registration with configuration"() {
        given:
        def valueSnapshotArray = [new StringValueSnapshot("EXTRA_1"), new StringValueSnapshot("EXTRA_2")] as ValueSnapshot[]

        when:
        registry.registerTransform {
            it.from.attribute(TEST_ATTRIBUTE, "FROM")
            it.to.attribute(TEST_ATTRIBUTE, "TO")
            it.artifactTransform(TestArtifactTransformWithParams) { artifactConfig ->
                artifactConfig.params("EXTRA_1", "EXTRA_2")
            }
        }

        then:
        1 * isolatableFactory.isolate(["EXTRA_1", "EXTRA_2"] as Object[]) >> new ArrayValueSnapshot(valueSnapshotArray)
        1 * classLoaderHierarchyHasher.getClassLoaderHash(TestArtifactTransform.classLoader) >> HashCode.fromInt(123)

        and:
        registry.transforms.size() == 1
        def registration = registry.transforms[0]
        registration.from.getAttribute(TEST_ATTRIBUTE) == "FROM"
        registration.to.getAttribute(TEST_ATTRIBUTE) == "TO"

        and:
        !outputFile.exists()

        when:
        def transformed = registration.transformationStep.transform(TransformationSubject.initial(TEST_INPUT)).files

        then:
        transformed.collect { it.name } == ['OUTPUT_FILE', 'EXTRA_1', 'EXTRA_2']
        transformed.each {
            assert it.exists()
            assert it.parentFile == outputDirectory
        }

        and:
        interaction {
            runTransformer(TEST_INPUT)
        }
    }

    def "fails when artifactTransform cannot be instantiated"() {
        given:
        def valueSnapshotArray = [] as ValueSnapshot[]

        when:
        registry.registerTransform {
            it.from.attribute(TEST_ATTRIBUTE, "FROM")
            it.to.attribute(TEST_ATTRIBUTE, "TO")
            it.artifactTransform(AbstractArtifactTransform)
        }

        then:
        1 * isolatableFactory.isolate([] as Object[]) >> new ArrayValueSnapshot(valueSnapshotArray)
        1 * classLoaderHierarchyHasher.getClassLoaderHash(AbstractArtifactTransform.classLoader) >> HashCode.fromInt(123)

        and:
        registry.transforms.size() == 1

        when:
        def registration = registry.transforms.first()
        def result = registration.transformationStep.transform(TransformationSubject.initial(TEST_INPUT))

        then:
        def failure = result.failure
        failure.message == "Failed to transform file 'input' using transform DefaultVariantTransformRegistryTest.AbstractArtifactTransform"
        failure.cause instanceof ObjectInstantiationException
        failure.cause.message == "Could not create an instance of type $AbstractArtifactTransform.name."

        and:
        interaction {
            runTransformer(TEST_INPUT)
        }
    }

    def "fails when incorrect number of artifactTransform parameters supplied for registration"() {
        given:
        def valueSnapshotArray = [new StringValueSnapshot("EXTRA_1"),
                                  new StringValueSnapshot("EXTRA_2"),
                                  new StringValueSnapshot("EXTRA_3")] as ValueSnapshot[]

        when:
        registry.registerTransform {
            it.from.attribute(TEST_ATTRIBUTE, "FROM")
            it.to.attribute(TEST_ATTRIBUTE, "TO")
            it.artifactTransform(TestArtifactTransformWithParams) { artifactConfig ->
                artifactConfig.params("EXTRA_1", "EXTRA_2")
                artifactConfig.params("EXTRA_3")
            }
        }

        then:
        1 * isolatableFactory.isolate(["EXTRA_1", "EXTRA_2", "EXTRA_3"] as Object[]) >> new ArrayValueSnapshot(valueSnapshotArray)
        1 * classLoaderHierarchyHasher.getClassLoaderHash(TestArtifactTransformWithParams.classLoader) >> HashCode.fromInt(123)

        and:
        registry.transforms.size() == 1

        when:
        def registration = registry.transforms.first()
        def failure = registration.transformationStep.transform(TransformationSubject.initial(TEST_INPUT)).failure

        then:
        failure instanceof TransformInvocationException
        failure.message == "Failed to transform file 'input' using transform DefaultVariantTransformRegistryTest.TestArtifactTransformWithParams"
        failure.cause instanceof ObjectInstantiationException
        failure.cause.message == "Could not create an instance of type $TestArtifactTransformWithParams.name."
        failure.cause.cause instanceof IllegalArgumentException
        failure.cause.cause.message == "Too many parameters provided for constructor for class ${TestArtifactTransformWithParams.name}. Expected 2, received 3."

        and:
        interaction {
            runTransformer(TEST_INPUT)
        }
    }

    def "fails when artifactTransform throws exception"() {
        given:
        def valueSnapshotArray = [] as ValueSnapshot[]

        when:
        registry.registerTransform {
            it.from.attribute(TEST_ATTRIBUTE, "FROM")
            it.to.attribute(TEST_ATTRIBUTE, "TO")
            it.artifactTransform(BrokenTransform)
        }

        then:
        1 * isolatableFactory.isolate([] as Object[]) >> new ArrayValueSnapshot(valueSnapshotArray)
        1 * classLoaderHierarchyHasher.getClassLoaderHash(BrokenTransform.classLoader) >> HashCode.fromInt(123)

        and:
        registry.transforms.size() == 1

        when:
        def registration = registry.transforms.first()
        def failure = registration.transformationStep.transform(TransformationSubject.initial(TEST_INPUT)).failure

        then:
        failure instanceof TransformInvocationException
        failure.message == "Failed to transform file 'input' using transform DefaultVariantTransformRegistryTest.BrokenTransform"
        failure.cause instanceof RuntimeException
        failure.cause.message == 'broken'

        and:
        interaction {
            runTransformer(TEST_INPUT)
        }
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
            it.artifactTransform(TestArtifactTransform)
            it.artifactTransform(TestArtifactTransform)
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
            it.artifactTransform(TestArtifactTransform)
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
            it.artifactTransform(TestArtifactTransform)
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
            it.artifactTransform(TestArtifactTransform)
        }

        then:
        def e = thrown(VariantTransformConfigurationException)
        e.message == "Could not register transform: each 'to' attribute must be included as a 'from' attribute."
        e.cause == null
    }

    private void runTransformer(File primaryInput) {
        1 * transformedFileCache.getResult(primaryInput, _, _)  >> { file, transformer, subject -> return transformer.transform(file, outputDirectory) }
    }

    static class TestArtifactTransform extends ArtifactTransform {
        @Override
        List<File> transform(File input) {
            assert input == TEST_INPUT
            def outputFile = new File(outputDirectory, 'OUTPUT_FILE')
            outputFile << "tmp"
            [outputFile]
        }
    }

    static class TestArtifactTransformWithParams extends ArtifactTransform {
        def outputFiles = ['OUTPUT_FILE']

        @Inject
        TestArtifactTransformWithParams(String extra1, String extra2) {
            outputFiles << extra1 << extra2
        }

        @Override
        List<File> transform(File input) {
            assert input == TEST_INPUT
            return outputFiles.collect { outputFileName ->
                def outputFile = new File(outputDirectory, outputFileName)
                outputFile << "tmp"
                outputFile
            }
        }
    }

    static class BrokenTransform extends ArtifactTransform {
        @Override
        List<File> transform(File input) {
            throw new RuntimeException("broken")
        }
    }

    static abstract class AbstractArtifactTransform extends ArtifactTransform {}
}
