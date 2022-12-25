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

package org.gradle.api.internal.artifacts.ivyservice.modulecache

import com.google.common.collect.Maps
import org.apache.commons.io.output.ByteArrayOutputStream
import org.gradle.api.internal.artifacts.DefaultImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.DependencyManagementTestUtil
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.DescriptorParseContext
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.GradleModuleMetadataParser
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.GradlePomModuleDescriptorParser
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.IvyModuleDescriptorConverter
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.IvyXmlModuleDescriptorParser
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.MetaDataParser
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionComparator
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionSelectorScheme
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.MavenVersionSelectorScheme
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.DesugaredAttributeContainerSerializer
import org.gradle.api.internal.artifacts.repositories.metadata.IvyMutableModuleMetadataFactory
import org.gradle.api.internal.artifacts.repositories.metadata.MavenMutableModuleMetadataFactory
import org.gradle.api.internal.file.TestFiles
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata
import org.gradle.internal.component.external.model.ivy.MutableIvyModuleResolveMetadata
import org.gradle.internal.component.external.model.maven.MutableMavenModuleResolveMetadata
import org.gradle.internal.component.model.MutableModuleSources
import org.gradle.internal.hash.Hashing
import org.gradle.internal.resource.local.FileResourceRepository
import org.gradle.internal.resource.local.LocalFileStandInExternalResource
import org.gradle.internal.resource.local.LocallyAvailableExternalResource
import org.gradle.internal.serialize.InputStreamBackedDecoder
import org.gradle.internal.serialize.OutputStreamBackedEncoder
import org.gradle.util.AttributeTestUtil
import org.gradle.util.TestUtil
import spock.lang.Specification

class ModuleMetadataSerializerTest extends Specification {

    private final ImmutableModuleIdentifierFactory moduleIdentifierFactory = new DefaultImmutableModuleIdentifierFactory()
    private final MavenMutableModuleMetadataFactory mavenMetadataFactory = DependencyManagementTestUtil.mavenMetadataFactory()
    private final IvyMutableModuleMetadataFactory ivyMetadataFactory = DependencyManagementTestUtil.ivyMetadataFactory()
    private final ModuleMetadataSerializer serializer = moduleMetadataSerializer()
    private GradlePomModuleDescriptorParser pomModuleDescriptorParser = pomParser()
    private MetaDataParser<MutableIvyModuleResolveMetadata> ivyDescriptorParser = ivyParser()
    private GradleModuleMetadataParser gradleMetadataParser = gradleMetadataParser()

    def "all samples are different"() {
        given:
        def metadata = sampleFiles().collectEntries { [it.name, parse(it)] }

        when:
        def serializedMetadata = metadata.collectEntries { [it.key, Hashing.sha1().hashBytes(serialize(it.value))] }

        then:
        println "Checking that all ${metadata.size()} samples are different"
        serializedMetadata.each { key, value ->
            println "$key : ${value.toString() }"
        }
        def unique = serializedMetadata.values() as Set
        unique.size() == metadata.size()
    }

    def "can write and re-read sample #sample.parentFile.name metadata file #sample.name"() {
        given:
        def metadata = parse(sample)
        def bytes = serialize(metadata)

        when:
        def deserializedMetadata = deserialize(bytes).asImmutable()
        def originMetadata = metadata.asImmutable()

        then:
        deserializedMetadata == originMetadata

        where:
        sample << sampleFiles()

    }

    private MutableModuleComponentResolveMetadata deserialize(byte[] serializedForm) {
        serializer.read(new InputStreamBackedDecoder(new ByteArrayInputStream(serializedForm)), moduleIdentifierFactory, Maps.newHashMap())
    }

    private byte[] serialize(MutableModuleComponentResolveMetadata metadata) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream()
        serializer.write(new OutputStreamBackedEncoder(baos), metadata.asImmutable(), Maps.newHashMap())
        baos.toByteArray()
    }

    static List<File> sampleFiles() {
        def baseUrl = ModuleMetadataSerializerTest.getResource("${this.simpleName}")
        def samples = []
        new File(baseUrl.toURI()).eachFile {
            samples.addAll(it.listFiles() as List)
        }

        String filter = System.getProperty('org.gradle.internal.test.moduleMetadataSerializerFilter', null)
        if (filter) {
            samples = samples.findAll { it =~ filter }
        }
        samples
    }

    MutableModuleComponentResolveMetadata parse(File file) {
        switch (file.parentFile.name) {
            case 'pom':
                return removeSources(parsePom(file))
            case 'ivy':
                return removeSources(parseIvy(file))
            case 'gradle':
                return removeSources(parseGradle(file))
        }
        throw new IllegalStateException("Unexpected metadata file $file")
    }

    MutableModuleComponentResolveMetadata removeSources(MutableModuleComponentResolveMetadata md) {
        md.sources = new MutableModuleSources()
        md
    }

    MutableMavenModuleResolveMetadata parsePom(File pomFile) {
        pomModuleDescriptorParser.parseMetaData(Mock(DescriptorParseContext), resource(pomFile)).result
    }

    MutableIvyModuleResolveMetadata parseIvy(File ivyFile) {
        ivyDescriptorParser.parseMetaData(Stub(DescriptorParseContext), resource(ivyFile)).result
    }

    MutableModuleComponentResolveMetadata parseGradle(File gradleFile) {
        def metadata = mavenMetadataFactory.create(DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId('test', 'test-module'), '1.0'), [])
        gradleMetadataParser.parse(resource(gradleFile), metadata)
        metadata
    }

    LocallyAvailableExternalResource resource(File testFile) {
        return new LocalFileStandInExternalResource(testFile, TestFiles.fileSystem())
    }

    private ModuleMetadataSerializer moduleMetadataSerializer() {
        new ModuleMetadataSerializer(
                new DesugaredAttributeContainerSerializer(AttributeTestUtil.attributesFactory(), TestUtil.objectInstantiator()),
                mavenMetadataFactory,
                ivyMetadataFactory,
                new ModuleSourcesSerializer([:])
        )
    }

    private GradlePomModuleDescriptorParser pomParser() {
        new GradlePomModuleDescriptorParser(
            new MavenVersionSelectorScheme(new DefaultVersionSelectorScheme(new DefaultVersionComparator(), new VersionParser())),
            moduleIdentifierFactory,
            Stub(FileResourceRepository),
            mavenMetadataFactory
        )
    }

    private IvyXmlModuleDescriptorParser ivyParser() {
        new IvyXmlModuleDescriptorParser(
            new IvyModuleDescriptorConverter(moduleIdentifierFactory),
            moduleIdentifierFactory,
            Stub(FileResourceRepository),
            ivyMetadataFactory
        )
    }

    private GradleModuleMetadataParser gradleMetadataParser() {
        new GradleModuleMetadataParser(
            AttributeTestUtil.attributesFactory(),
            moduleIdentifierFactory,
            TestUtil.objectInstantiator()
        )
    }
}
