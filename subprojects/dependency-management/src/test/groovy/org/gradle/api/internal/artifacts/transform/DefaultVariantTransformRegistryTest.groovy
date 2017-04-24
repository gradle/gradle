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

import com.google.common.hash.HashCode
import org.gradle.api.artifacts.transform.ArtifactTransform
import org.gradle.api.artifacts.transform.ArtifactTransformException
import org.gradle.api.artifacts.transform.VariantTransformConfigurationException
import org.gradle.api.attributes.Attribute
import org.gradle.api.internal.attributes.DefaultImmutableAttributesFactory
import org.gradle.api.internal.changedetection.state.StringValueSnapshot
import org.gradle.api.internal.changedetection.state.ValueSnapshotter
import org.gradle.internal.classloader.ClassLoaderHierarchyHasher
import org.gradle.internal.reflect.ObjectInstantiationException
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
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
    def transformedFileCache = Mock(TransformedFileCache)
    def valueSnapshotter = Mock(ValueSnapshotter)
    def classLoaderHierarchyHasher = Mock(ClassLoaderHierarchyHasher)
    def attributesFactory = new DefaultImmutableAttributesFactory()
    def registry = new DefaultVariantTransformRegistry(instantiatorFactory, attributesFactory, transformedFileCache, valueSnapshotter, classLoaderHierarchyHasher)

    def "creates registration without configuration"() {
        when:
        registry.registerTransform {
            it.from.attribute(TEST_ATTRIBUTE, "FROM")
            it.to.attribute(TEST_ATTRIBUTE, "TO")
            it.artifactTransform(TestArtifactTransform)
        }

        then:
        1 * valueSnapshotter.snapshot([] as Object[]) >> new StringValueSnapshot("inputs")
        1 * classLoaderHierarchyHasher.getClassLoaderHash(TestArtifactTransform.classLoader) >> HashCode.fromInt(123)

        and:
        registry.transforms.size() == 1
        def registration = registry.transforms[0]
        registration.from.getAttribute(TEST_ATTRIBUTE) == "FROM"
        registration.to.getAttribute(TEST_ATTRIBUTE) == "TO"

        and:
        !outputFile.exists()

        when:
        def transformed = registration.artifactTransform.transform(TEST_INPUT)

        then:
        transformed.size() == 1
        transformed.first() == new File(outputDirectory, "OUTPUT_FILE")

        and:
        1 * transformedFileCache.getResult(TEST_INPUT, _, _) >> { file, impl, transform -> return transform.apply(file, outputDirectory) }
    }

    def "creates registration with configuration"() {
        when:
        registry.registerTransform {
            it.from.attribute(TEST_ATTRIBUTE, "FROM")
            it.to.attribute(TEST_ATTRIBUTE, "TO")
            it.artifactTransform(TestArtifactTransformWithParams) { artifactConfig ->
                artifactConfig.params("EXTRA_1", "EXTRA_2")
            }
        }

        then:
        1 * valueSnapshotter.snapshot(["EXTRA_1", "EXTRA_2"] as Object[]) >> new StringValueSnapshot("inputs")
        1 * classLoaderHierarchyHasher.getClassLoaderHash(TestArtifactTransform.classLoader) >> HashCode.fromInt(123)

        and:
        registry.transforms.size() == 1
        def registration = registry.transforms[0]
        registration.from.getAttribute(TEST_ATTRIBUTE) == "FROM"
        registration.to.getAttribute(TEST_ATTRIBUTE) == "TO"

        and:
        !outputFile.exists()

        when:
        def transformed = registration.artifactTransform.transform(TEST_INPUT)

        then:
        transformed.collect { it.name } == ['OUTPUT_FILE', 'EXTRA_1', 'EXTRA_2']
        transformed.each {
            assert it.exists()
            assert it.parentFile == outputDirectory
        }

        and:
        1 * transformedFileCache.getResult(TEST_INPUT, _, _) >> { file, impl, transform -> return transform.apply(file, outputDirectory) }
    }

    def "fails when artifactTransform cannot be instantiated"() {
        when:
        registry.registerTransform {
            it.to.attribute(TEST_ATTRIBUTE, "TO")
            it.artifactTransform(AbstractArtifactTransform)
        }

        then:
        1 * valueSnapshotter.snapshot([] as Object[]) >> new StringValueSnapshot("inputs")
        1 * classLoaderHierarchyHasher.getClassLoaderHash(AbstractArtifactTransform.classLoader) >> HashCode.fromInt(123)

        and:
        registry.transforms.size() == 1

        when:
        def registration = registry.transforms.first()
        registration.artifactTransform.transform(TEST_INPUT)

        then:
        def e = thrown(ArtifactTransformException)
        e.message == "Failed to transform file 'input' to match attributes {TEST=TO} using transform DefaultVariantTransformRegistryTest.AbstractArtifactTransform"
        e.cause instanceof ObjectInstantiationException
        e.cause.message == "Could not create an instance of type $AbstractArtifactTransform.name."

        and:
        1 * transformedFileCache.getResult(TEST_INPUT, _, _) >> { file, impl, transform -> return transform.apply(file, outputDirectory) }
    }

    def "fails when incorrect number of artifactTransform parameters supplied for registration"() {
        when:
        registry.registerTransform {
            it.to.attribute(TEST_ATTRIBUTE, "TO")
            it.artifactTransform(TestArtifactTransformWithParams) { artifactConfig ->
                artifactConfig.params("EXTRA_1", "EXTRA_2")
                artifactConfig.params("EXTRA_3")
            }
        }

        then:
        1 * valueSnapshotter.snapshot(["EXTRA_1", "EXTRA_2", "EXTRA_3"] as Object[]) >> new StringValueSnapshot("inputs")
        1 * classLoaderHierarchyHasher.getClassLoaderHash(TestArtifactTransformWithParams.classLoader) >> HashCode.fromInt(123)

        and:
        registry.transforms.size() == 1

        when:
        def registration = registry.transforms.first()
        registration.artifactTransform.transform(TEST_INPUT)

        then:
        def e = thrown(ArtifactTransformException)
        e.message == "Failed to transform file 'input' to match attributes {TEST=TO} using transform DefaultVariantTransformRegistryTest.TestArtifactTransformWithParams"
        e.cause instanceof ObjectInstantiationException
        e.cause.message == "Could not create an instance of type $TestArtifactTransformWithParams.name."
        e.cause.cause instanceof IllegalArgumentException
        e.cause.cause.message == "Too many parameters provided for constructor for class ${TestArtifactTransformWithParams.name}. Expected 2, received 3."

        and:
        1 * transformedFileCache.getResult(TEST_INPUT, _, _) >> { file, impl, transform -> return transform.apply(file, outputDirectory) }
    }

    def "fails when artifactTransform throws exception"() {
        when:
        registry.registerTransform {
            it.to.attribute(TEST_ATTRIBUTE, "TO")
            it.artifactTransform(BrokenTransform)
        }

        then:
        1 * valueSnapshotter.snapshot([] as Object[]) >> new StringValueSnapshot("inputs")
        1 * classLoaderHierarchyHasher.getClassLoaderHash(BrokenTransform.classLoader) >> HashCode.fromInt(123)

        and:
        registry.transforms.size() == 1

        when:
        def registration = registry.transforms.first()
        registration.artifactTransform.transform(TEST_INPUT)

        then:
        def e = thrown(ArtifactTransformException)
        e.message == "Failed to transform file 'input' to match attributes {TEST=TO} using transform DefaultVariantTransformRegistryTest.BrokenTransform"
        e.cause instanceof RuntimeException
        e.cause.message == 'broken'

        and:
        1 * transformedFileCache.getResult(TEST_INPUT, _, _) >> { file, impl, transform -> return transform.apply(file, outputDirectory) }
    }

    def "fails when artifactTransform configuration action fails for registration"() {
        def failure = new RuntimeException("Bad config")

        when:
        registry.registerTransform {
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
        e.message == 'Could not register transform: ArtifactTransform must be provided for registration.'
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
