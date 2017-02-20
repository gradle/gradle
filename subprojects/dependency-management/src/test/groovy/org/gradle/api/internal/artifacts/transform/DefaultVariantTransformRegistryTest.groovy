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
import org.gradle.api.artifacts.transform.VariantTransformConfigurationException
import org.gradle.api.attributes.Attribute
import org.gradle.api.internal.attributes.DefaultImmutableAttributesFactory
import org.gradle.internal.Factories
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.model.internal.type.ModelType
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class DefaultVariantTransformRegistryTest extends Specification {
    public static final TEST_ATTRIBUTE = Attribute.of("TEST", String)
    public static final TEST_INPUT = new File("input");

    @Rule
    final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()

    def instantiator = DirectInstantiator.INSTANCE
    def outputDirectory = tmpDir.createDir("OUTPUT_DIR")
    def outputFile = outputDirectory.file('OUTPUT_FILE')
    def attributesFactory = new DefaultImmutableAttributesFactory()
    def registry = new DefaultVariantTransformRegistry(instantiator, Factories.constant(outputDirectory), attributesFactory)


    def "creates registration without configuration"() {
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

        and:
        !outputFile.exists()

        when:
        def transformed = registration.artifactTransform.transform(TEST_INPUT)

        then:
        transformed == [outputFile]
        outputFile.exists()
    }

    def "creates registration with configuration"() {
        when:
        registry.registerTransform {
            it.from.attribute(TEST_ATTRIBUTE, "FROM")
            it.to.attribute(TEST_ATTRIBUTE, "TO")
            it.artifactTransform(TestArtifactTransform) { artifactConfig ->
                artifactConfig.params("EXTRA_1", "EXTRA_2")
            }
        }

        then:
        registry.transforms.size() == 1
        def registration = registry.transforms[0]
        registration.from.getAttribute(TEST_ATTRIBUTE) == "FROM"
        registration.to.getAttribute(TEST_ATTRIBUTE) == "TO"

        and:
        !outputFile.exists()

        when:
        def transformed = registration.artifactTransform.transform(TEST_INPUT)

        then:
        transformed == outputDirectory.files('OUTPUT_FILE', 'EXTRA_1', 'EXTRA_2')
        transformed.each {
            assert it.exists()
        }
    }

    def "fails when artifactTransform cannot be instantiated for registration"() {
        when:
        registry.registerTransform {
            it.artifactTransform(AbstractArtifactTransform)
        }

        then:
        def e = thrown(VariantTransformConfigurationException)
        e.message == 'Could not create instance of ' + ModelType.of(AbstractArtifactTransform).displayName + '.'
        e.cause instanceof InstantiationException
    }

    def "fails when incorrect number of artifactTransform parameters supplied for registration"() {
        when:
        registry.registerTransform {
            it.artifactTransform(TestArtifactTransform) { artifactConfig ->
                artifactConfig.params("EXTRA_1", "EXTRA_2")
                artifactConfig.params("EXTRA_3")
            }
        }

        then:
        def e = thrown(VariantTransformConfigurationException)
        e.message == 'Could not create instance of ' + ModelType.of(TestArtifactTransform).displayName + '.'
        e.cause instanceof IllegalArgumentException
        e.cause.message == 'Could not find any public constructor for ' + TestArtifactTransform + ' which accepts parameters [java.lang.String, java.lang.String, java.lang.String].'
    }

    def "fails when artifactTransform configuration action fails for registration"() {
        when:
        registry.registerTransform {
            it.artifactTransform(TestArtifactTransform) { artifactConfig ->
                throw new RuntimeException("Bad config")
            }
        }

        then:
        def e = thrown(VariantTransformConfigurationException)
        e.message == 'Could not create instance of ' + ModelType.of(TestArtifactTransform).displayName + '.'
        e.cause instanceof RuntimeException
        e.cause.message == 'Bad config'
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
        def outputFiles = ['OUTPUT_FILE']

        TestArtifactTransform() {
        }

        TestArtifactTransform(String extra1, String extra2) {
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

    static abstract class AbstractArtifactTransform extends ArtifactTransform {}
}
