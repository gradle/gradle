/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser

import com.google.common.collect.LinkedHashMultimap
import com.google.common.collect.SetMultimap
import org.apache.ivy.plugins.matcher.PatternMatcher
import org.gradle.api.internal.artifacts.DefaultImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.DependencyManagementTestUtil
import org.gradle.api.internal.artifacts.dependencies.DefaultMutableVersionConstraint
import org.gradle.api.internal.artifacts.ivyservice.NamespaceId
import org.gradle.api.internal.artifacts.repositories.metadata.IvyMutableModuleMetadataFactory
import org.gradle.api.internal.file.TestFiles
import org.gradle.internal.component.external.descriptor.Artifact
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.external.model.ivy.IvyDependencyDescriptor
import org.gradle.internal.component.external.model.ivy.MutableIvyModuleResolveMetadata
import org.gradle.internal.resource.local.FileResourceRepository
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.internal.Resources
import org.junit.Rule
import spock.lang.Issue
import spock.lang.Specification

import static org.gradle.api.internal.component.ArtifactType.IVY_DESCRIPTOR
import static org.gradle.internal.component.external.model.DefaultModuleComponentSelector.newSelector
import static org.gradle.util.internal.TextUtil.normaliseFileSeparators

class IvyXmlModuleDescriptorParserTest extends Specification {
    @Rule
    public final Resources resources = new Resources()
    @Rule TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())

    DefaultImmutableModuleIdentifierFactory moduleIdentifierFactory = new DefaultImmutableModuleIdentifierFactory()
    FileResourceRepository fileRepository = TestFiles.fileRepository()
    IvyMutableModuleMetadataFactory metadataFactory = DependencyManagementTestUtil.ivyMetadataFactory()

    IvyXmlModuleDescriptorParser parser = new IvyXmlModuleDescriptorParser(new IvyModuleDescriptorConverter(moduleIdentifierFactory), moduleIdentifierFactory, fileRepository, metadataFactory)

    DescriptorParseContext parseContext = Mock()
    MutableIvyModuleResolveMetadata metadata
    boolean hasGradleMetadataRedirectionMarker

    def "parses minimal Ivy descriptor"() {
        when:
        def file = temporaryFolder.file("ivy.xml") << """
<ivy-module version="1.0">
    <info organisation="myorg"
          module="mymodule"
          revision="myrev"
    />
</ivy-module>
"""
        parse(parseContext, file)

        then:
        metadata.id == componentId("myorg", "mymodule", "myrev")
        metadata.status == "integration"
        metadata.configurationDefinitions.keySet() == ["default"] as Set
        metadata.dependencies.empty
        !hasGradleMetadataRedirectionMarker

        artifact()
    }

    def "adds implicit configurations"() {
        when:
        def file = temporaryFolder.file("ivy.xml") << """
<ivy-module version="1.0">
    <info organisation="myorg"
          module="mymodule"
          revision="myrev"
          status="integration"
          publication="20041101110000"
    />
    <dependencies>
    </dependencies>
</ivy-module>
"""
        parse(parseContext, file)

        then:
        metadata.id == componentId("myorg", "mymodule", "myrev")
        metadata.status == "integration"
        metadata.configurationDefinitions.keySet() == ["default"] as Set
        metadata.dependencies.empty

        def artifact = artifact()
        artifact.artifactName.name == "mymodule"
        artifact.artifactName.type == "jar"
        artifact.configurations == ["default"] as Set
    }

    def "adds implicit artifact when none declared"() {
        when:
        def file = temporaryFolder.file("ivy.xml") << """
<ivy-module version="1.0">
    <info organisation="myorg"
          module="mymodule"
          revision="myrev"
    />
    <configurations>
        <conf name="A"/>
        <conf name="B"/>
    </configurations>
</ivy-module>
"""
        parse(parseContext, file)

        then:
        metadata.id == componentId("myorg", "mymodule", "myrev")
        metadata.status == "integration"
        metadata.configurationDefinitions.keySet() == ["A", "B"] as Set
        metadata.dependencies.empty

        def artifact = artifact()
        artifact.artifactName.name == 'mymodule'
        artifact.artifactName.type == 'jar'
        artifact.artifactName.extension == 'jar'
        artifact.configurations == ["A", "B"] as Set
    }

    def "fails when ivy.xml uses unknown version of descriptor format"() {
        def file = temporaryFolder.file("ivy.xml") << """
<ivy-module version="unknown">
    <info organisation="myorg"
          module="mymodule"
    />
</ivy-module>
"""

        when:
        parse(parseContext, file)

        then:
        def e = thrown(MetaDataParseException)
        e.message == "Could not parse Ivy file ${file}"
        e.cause.message == "invalid version unknown"
    }

    def "fails when configuration extends an unknown configuration"() {
        def file = temporaryFolder.file("ivy.xml") << """
<ivy-module version="1.0">
    <info organisation="myorg"
          module="mymodule"
          status="integration"
    />
    <configurations>
        <conf name="A" extends="invalidConf"/>
    </configurations>
</ivy-module>
"""

        when:
        parse(parseContext, file)

        then:
        def e = thrown(MetaDataParseException)
        e.message == "Could not parse Ivy file ${file}"
        e.cause.message == "Configuration 'A' extends configuration 'invalidConf' which is not declared."
    }

    def "fails when artifact is mapped to an unknown configuration"() {
        def file = temporaryFolder.file("ivy.xml") << """
<ivy-module version="1.0">
    <info organisation="myorg"
          module="mymodule"
          status="integration"
    />
    <configurations>
        <conf name="A"/>
    </configurations>
    <publications>
        <artifact conf="A,unknown"/>
    </publications>
</ivy-module>
"""

        when:
        parse(parseContext, file)

        then:
        def e = thrown(MetaDataParseException)
        e.message == "Could not parse Ivy file ${file}"
        e.cause.message == "Artifact mymodule.jar is mapped to configuration 'unknown' which is not declared."
    }

    def "fails when exclude is mapped to an unknown configuration"() {
        def file = temporaryFolder.file("ivy.xml") << """
<ivy-module version="1.0">
    <info organisation="myorg"
          module="mymodule"
          status="integration"
    />
    <configurations>
        <conf name="A"/>
    </configurations>
    <dependencies>
        <exclude org="other" conf="A,unknown"/>
    </dependencies>
</ivy-module>
"""

        when:
        parse(parseContext, file)

        then:
        def e = thrown(MetaDataParseException)
        e.message == "Could not parse Ivy file ${file}"
        e.cause.message == "Exclude rule other#*!*.* is mapped to configuration 'unknown' which is not declared."
    }

    def "fails when dependency is mapped from an unknown configuration"() {
        def file = temporaryFolder.file("ivy.xml") << """
<ivy-module version="1.0">
    <info organisation="myorg"
          module="mymodule"
          status="integration"
    />
    <configurations>
        <conf name="A"/>
    </configurations>
    <dependencies>
        <dependency name="other" rev="1.2" conf="A,unknown->%"/>
    </dependencies>
</ivy-module>
"""

        when:
        parse(parseContext, file)

        then:
        def e = thrown(MetaDataParseException)
        e.message == "Could not parse Ivy file ${file}"
        e.cause.message.contains("Cannot add dependency 'myorg#other;1.2' to configuration 'unknown'")
    }

    def "fails when there is a cycle in configuration hierarchy"() {
        def file = temporaryFolder.file("ivy.xml") << """
<ivy-module version="1.0">
    <info organisation="myorg"
          module="mymodule"
          status="integration"
    />
    <configurations>
        <conf name="A" extends="B"/>
        <conf name="B" extends="A"/>
    </configurations>
</ivy-module>
"""

        when:
        parse(parseContext, file)

        then:
        def e = thrown(MetaDataParseException)
        e.message == "Could not parse Ivy file ${file}"
        e.cause.message == "illegal cycle detected in configuration extension: A => B => A"
    }

    def "fails when descriptor contains badly formed XML"() {
        def file = temporaryFolder.file("ivy.xml") << """
<ivy-module version="1.0">
    <info
</ivy-module>
"""

        when:
        parse(parseContext, file)

        then:
        def e = thrown(MetaDataParseException)
        e.message == "Could not parse Ivy file ${file}"
        e.cause.message.contains('"info"')
    }

    def "fails when descriptor does not match schema"() {
        def file = temporaryFolder.file("ivy.xml") << """
<ivy-module version="1.0">
    <not-an-ivy-file/>
</ivy-module>
"""

        when:
        parse(parseContext, file)

        then:
        def e = thrown(MetaDataParseException)
        e.message == "Could not parse Ivy file ${file}"
        e.cause.message.contains('Not a valid Ivy file')
    }

    def "fails when descriptor does not declare module version id"() {
        def file = temporaryFolder.file("ivy.xml") << xml
        when:
        parse(parseContext, file)

        then:
        def e = thrown(MetaDataParseException)
        e.message == "Could not parse Ivy file ${file}"
        e.cause.message.contains('null name not allowed')

        where:
        xml << [
            """<ivy-module version="1.0">
                <info>
            </ivy-module>"""
        ]
    }

    def "parses a full Ivy descriptor"() {
        def file = temporaryFolder.file("ivy.xml")
        file.text = resources.getResource("test-full.xml").text

        when:
        parse(parseContext, file)

        then:
        metadata.id == componentId("myorg", "mymodule", "myrev")
        metadata.status == "status"

        metadata.extraAttributes.size() == 1
        metadata.extraAttributes.get(new NamespaceId("http://ant.apache.org/ivy/extra", "someExtra")) == "56576"

        metadata.configurationDefinitions.size() == 5
        assertConf("myconf1", "desc 1", true, new String[0])
        assertConf("myconf2", "desc 2", true, new String[0])
        assertConf("myconf3", "desc 3", false, new String[0])
        assertConf("myconf4", "desc 4", true, ["myconf1", "myconf2"].toArray(new String[2]))
        assertConf("myoldconf", "my old desc", true, new String[0])

        metadata.artifactDefinitions.size() == 4
        assertArtifacts("myconf1", ["myartifact1", "myartifact2", "myartifact3", "myartifact4"])
        assertArtifacts("myconf2", ["myartifact1", "myartifact3"])
        assertArtifacts("myconf3", ["myartifact1", "myartifact3", "myartifact4"])
        assertArtifacts("myconf4", ["myartifact1"])

        assertArtifact('myartifact1', 'jar', 'jar', 'classy1')
        assertArtifact('myartifact2', 'jar', 'jar', 'classy2')
        assertArtifact('myartifact3', 'jar', 'jar', null)

        metadata.dependencies.size() == 13

        verifyFullDependencies(metadata.dependencies)

        def rules = metadata.excludes
        rules.size() == 2
        rules[0].matcher == PatternMatcher.GLOB
        rules[0].configurations as List == ["myconf1"]
        rules[1].matcher == PatternMatcher.EXACT
        rules[1].configurations as List == ["myconf1", "myconf2", "myconf3", "myconf4", "myoldconf"]
    }

    def "merges values from parent Ivy descriptor"() {
        given:
        def parentFile = temporaryFolder.file("parent.xml") << """
<ivy-module version="1.0">
    <info organisation="myorg"
          module="parent"
          revision="parentrev"
          status="not-the-default"
          publication="20041101110000"
    />
    <configurations>
        <conf name='default'/>
    </configurations>
    <dependencies>
        <dependency conf="*->*" org="deporg" name="depname" rev="deprev"/>
    </dependencies>
</ivy-module>
"""
        def file = temporaryFolder.file("ivy.xml") << """
<ivy-module version="1.0">
    <info module="mymodule" revision="myrev">
        <extends organisation="myorg" module="parent" revision="parentrev"/>
    </info>
</ivy-module>
"""
        and:
        parseContext.getMetaDataArtifact(_, IVY_DESCRIPTOR) >> fileRepository.resource(parentFile)

        when:
        parse(parseContext, file)

        then:
        metadata.id == componentId("myorg", "mymodule", "myrev")
        metadata.status == "integration"
        metadata.configurationDefinitions.keySet() == ["default"] as Set

        metadata.dependencies.size() == 1
        def dependency = metadata.dependencies.first()
        dependency.selector == newSelector(DefaultModuleIdentifier.newId("deporg", "depname"), new DefaultMutableVersionConstraint("deprev"))
    }

    @Issue("https://issues.gradle.org/browse/GRADLE-2766")
    def "defaultconfmapping is respected"() {
        given:
        def file = temporaryFolder.createFile("ivy.xml")
        file.text = """
           <ivy-module version="2.0" xmlns:e="http://ant.apache.org/ivy/extra">
                <info organisation="myorg"
                      module="mymodule"
                      revision="myrev"
                      status="integration"
                      publication="20041101110000">
                </info>
                <configurations>
                    <conf name="myconf" />
                </configurations>
                <publications/>
                <dependencies defaultconfmapping="myconf->default">
                    <dependency name="mymodule2" rev="1.2"/>
                </dependencies>
            </ivy-module>
        """

        when:
        parse(parseContext, file)
        def dependency = metadata.dependencies.first()

        then:
        dependency.confMappings.keySet() == ["myconf"] as Set
    }

    def "defaultconf is respected"() {
        given:
        def file = temporaryFolder.createFile("ivy.xml")
        file.text = """
           <ivy-module version="2.0" xmlns:e="http://ant.apache.org/ivy/extra">
                <info organisation="myorg"
                      module="mymodule"
                      revision="myrev"
                      status="integration"
                      publication="20041101110000">
                </info>
                <configurations>
                    <conf name="conf1" />
                    <conf name="conf2" />
                </configurations>
                <publications defaultconf="conf2">
                    <artifact/>
                </publications>
            </ivy-module>
        """

        when:
        parse(parseContext, file)

        then:
        artifact().configurations == ["conf2"] as Set
    }

    def "parses dependency config mappings"() {
        given:
        def file = temporaryFolder.createFile("ivy.xml")
        file.text = """
           <ivy-module version="2.0" xmlns:e="http://ant.apache.org/ivy/extra">
                <info organisation="myorg"
                      module="mymodule"
                      revision="myrev">
                </info>
                <configurations>
                    <conf name="a" />
                    <conf name="b" />
                    <conf name="c" />
                </configurations>
                <publications/>
                <dependencies>
                    <dependency name="mymodule2" rev="1.2" conf="a"/>
                    <dependency name="mymodule2" rev="1.2" conf="a->other"/>
                    <dependency name="mymodule2" rev="1.2" conf="*->@"/>
                    <dependency name="mymodule2" rev="1.2" conf="a->other;%->@"/>
                    <dependency name="mymodule2" rev="1.2" conf="*,!a->@"/>
                    <dependency name="mymodule2" rev="1.2" conf="a->*"/>
                    <dependency name="mymodule2" rev="1.2" conf="a->one,two;a,b->three;*->four;%->none"/>
                    <dependency name="mymodule2" rev="1.2" conf="a->#"/>
                    <dependency name="mymodule2" rev="1.2" conf="a->a;%->@"/>
                    <dependency name="mymodule2" rev="1.2" conf="a->a;*,!a->b"/>
                    <dependency name="mymodule2" rev="1.2" conf="*->*"/>
                    <dependency name="mymodule2" rev="1.2" conf=""/>
                    <dependency name="mymodule2" rev="1.2"/>
                </dependencies>
            </ivy-module>
        """

        when:
        parse(parseContext, file)

        then:
        metadata.dependencies[0].confMappings == map("a": ["a"])
        metadata.dependencies[1].confMappings == map("a": ["other"])
        metadata.dependencies[2].confMappings == map("*": ["@"])
        metadata.dependencies[3].confMappings == map("a": ["other"], "%": ["@"])
        metadata.dependencies[4].confMappings == map("*": ["@"], "!a": ["@"])
        metadata.dependencies[5].confMappings == map("a": ["*"])
        metadata.dependencies[6].confMappings == map("a": ["one", "two", "three"], "b": ["three"], "*": ["four"], "%": ["none"])
        metadata.dependencies[7].confMappings == map("a": ["#"])
        metadata.dependencies[8].confMappings == map("a": ["a"], "%": ["@"])
        metadata.dependencies[9].confMappings == map("a": ["a"], "*": ["b"], "!a": ["b"])
        metadata.dependencies[10].confMappings == map("*": ["*"])
        metadata.dependencies[11].confMappings == map("*": ["*"])
        metadata.dependencies[12].confMappings == map("*": ["*"])
    }

    def "parses dependency config mappings with defaults"() {
        given:
        def file = temporaryFolder.createFile("ivy.xml")
        file.text = """
           <ivy-module version="2.0" xmlns:e="http://ant.apache.org/ivy/extra">
                <info organisation="myorg"
                      module="mymodule"
                      revision="myrev">
                </info>
                <configurations>
                    <conf name="a" />
                    <conf name="b" />
                    <conf name="c" />
                </configurations>
                <publications/>
                <dependencies defaultconf="a" defaultconfmapping="a->a1;b->b1,b2">
                    <dependency name="mymodule2" rev="1.2"/>
                    <dependency name="mymodule2" rev="1.2" conf=""/>
                    <dependency name="mymodule2" rev="1.2" conf="a"/>
                    <dependency name="mymodule2" rev="1.2" conf="b"/>
                    <dependency name="mymodule2" rev="1.2" conf="a->other"/>
                    <dependency name="mymodule2" rev="1.2" conf="*->@"/>
                    <dependency name="mymodule2" rev="1.2" conf="c->other"/>
                    <dependency name="mymodule2" rev="1.2" conf="a->"/>
                </dependencies>
            </ivy-module>
        """

        when:
        parse(parseContext, file)

        then:
        metadata.dependencies[0].confMappings == map("a": ["a1"])
        metadata.dependencies[1].confMappings == map("a": ["a1"])
        metadata.dependencies[2].confMappings == map("a": ["a1"])
        metadata.dependencies[3].confMappings == map("b": ["b1", "b2"])
        metadata.dependencies[4].confMappings == map("a": ["other"])
        metadata.dependencies[5].confMappings == map("*": ["@"])
        metadata.dependencies[6].confMappings == map("c": ["other"])
        metadata.dependencies[7].confMappings == map("a": ["a1"])

    }

    def "parses artifact config mappings"() {
        given:
        def file = temporaryFolder.createFile("ivy.xml")
        file.text = """
           <ivy-module version="2.0">
                <info organisation="myorg"
                      module="mymodule"
                      revision="myrev">
                </info>
                <configurations>
                    <conf name="a" />
                    <conf name="b" />
                    <conf name="c" extends="a"/>
                    <conf name="d" />
                </configurations>
                <publications>
                    <artifact/>
                    <artifact name='art2' type='type' ext='ext' conf='*'/>
                    <artifact name='art3' type='type2' conf='a, b  '/>
                </publications>
            </ivy-module>
        """

        when:
        parse(parseContext, file)

        then:
        assertArtifacts("a", ["mymodule", "art2", "art3"])
        assertArtifacts("b", ["mymodule", "art2", "art3"])
        assertArtifacts("c", ["mymodule", "art2"])
        assertArtifacts("d", ["mymodule", "art2"])

        and:
        artifacts("a")*.artifactName*.name == ['mymodule', 'art2', 'art3']
        artifacts("a")*.artifactName*.type == ['jar', 'type', 'type2']
        artifacts("a")*.artifactName*.extension == ['jar', 'ext', 'type2']

        and:
        artifacts("b")*.artifactName*.name == ['mymodule', 'art2', 'art3']
        artifacts("c")*.artifactName*.name == ['mymodule', 'art2']
        artifacts("d")*.artifactName*.name == ['mymodule', 'art2']
    }

    def "parses artifact attributes"() {
        given:
        def file = temporaryFolder.createFile("ivy.xml")
        file.text = """
           <ivy-module version="2.0"
                    xmlns:m="http://ant.apache.org/ivy/maven"
                    xmlns:e="http://ant.apache.org/ivy/extra"
                    xmlns:arbitrary="http://anything.org">
                <info organisation="myorg"
                      module="mymodule"
                      revision="myrev">
                </info>
                <configurations>
                    <conf name="a" />
                </configurations>
                <publications>
                    <artifact/>
                    <artifact name='art2' type='type' ext='ext'/>
                    <artifact name='art3' type='type2' m:classifier='classy1'/>
                    <artifact name='art4' ext='ext' e:classifier='classy2'/>
                    <artifact name='art5' arbitrary:classifier='classy3'/>
                </publications>
            </ivy-module>
        """

        when:
        parse(parseContext, file)

        then:
        assertArtifacts("a", ["mymodule", "art2", "art3", "art4", "art5"])

        and:
        assertArtifact('mymodule', 'jar', 'jar', null)
        assertArtifact('art2', 'ext', 'type', null)
        assertArtifact('art3', 'type2', 'type2', 'classy1')
        assertArtifact('art4', 'ext', 'jar', 'classy2')
        assertArtifact('art5', 'jar', 'jar', 'classy3')
    }

    def "accumulates configurations if the same artifact listed more than once"() {
        given:
        def file = temporaryFolder.createFile("ivy.xml")
        file.text = """
           <ivy-module version="2.0">
                <info organisation="myorg" module="mymodule" revision="myrev"/>
                <configurations><conf name="a"/><conf name="b"/><conf name="c"/><conf name="d"/><conf name="e"/></configurations>
                <publications>
                    <artifact name='art' type='type' ext='ext' conf='a,b'/>
                    <artifact name='art' type='type' ext='ext' conf='b,c'/>
                </publications>
            </ivy-module>
        """

        when:
        parse(parseContext, file)

        then:
        artifact().configurations == ['a', 'b', 'c'] as Set
    }

    def "parses extra info"() {
        given:
        def file = temporaryFolder.createFile("ivy.xml")
        file.text = """
           <ivy-module version="2.0" xmlns:b="namespace-b" xmlns:c="namespace-c">
                <info organisation="myorg"
                      module="mymodule"
                      revision="myrev"
                      b:a="1"
                      b:b="2"
                      c:a="3">
                    <b:a>info 1</b:a>
                    <c:a>info 2</c:a>
                </info>
            </ivy-module>
        """

        when:
        parse(parseContext, file)

        then:
        metadata.id == componentId("myorg", "mymodule", "myrev")
        metadata.extraAttributes.size() == 2
        metadata.extraAttributes[new NamespaceId("namespace-b", "a")] == "info 1"
        metadata.extraAttributes[new NamespaceId("namespace-c", "a")] == "info 2"
    }

    def 'parses old gradle module metadata marker'() {
        when:
        def file = temporaryFolder.file("ivy.xml") << """
<ivy-module version="1.0">
    <!-- do-not-remove: published-with-gradle-metadata -->
    <info organisation="myorg"
          module="mymodule"
          revision="myrev"
    />
</ivy-module>
"""
        parse(parseContext, file)

        then:
        hasGradleMetadataRedirectionMarker
    }

    def 'parses gradle module metadata marker'() {
        when:
        def file = temporaryFolder.file("ivy.xml") << """
<ivy-module version="1.0">
    <!-- do_not_remove: published-with-gradle-metadata -->
    <info organisation="myorg"
          module="mymodule"
          revision="myrev"
    />
</ivy-module>
"""
        parse(parseContext, file)

        then:
        hasGradleMetadataRedirectionMarker
    }

    def "parse IVY descriptor with external entities"() {
        given:
        def externalFile = temporaryFolder.file('external.txt').createFile()
        def ivyXml = temporaryFolder.file("ivy.xml") << """
        <!DOCTYPE data [
          <!ENTITY file SYSTEM "file://${normaliseFileSeparators(externalFile.absolutePath)}">
        ]>
        <ivy-module version="1.0">
            <info organisation="myorg" module="mymodule" revision="myrev">
                &file;
            </info>
        </ivy-module>
        """

        when:
        parser.parseMetaData(parseContext, ivyXml)

        then:
        def e = thrown(MetaDataParseException)
        e.cause.message == "External Entity: Failed to read external document 'external.txt', because 'file' access is not allowed due to restriction set by the accessExternalDTD property."
    }

    private void parse(DescriptorParseContext parseContext, TestFile file) {
        def parseResult = parser.parseMetaData(parseContext, file)
        metadata = parseResult.result
        hasGradleMetadataRedirectionMarker = parseResult.hasGradleMetadataRedirectionMarker()

    }

    private Artifact artifact() {
        assert metadata.artifactDefinitions.size() == 1
        metadata.artifactDefinitions[0]
    }

    private List<Artifact> artifacts(String conf) {
        metadata.artifactDefinitions.findAll { it.configurations.contains(conf) }
    }

    static componentId(String group, String module, String version) {
        DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId(group, module), version)
    }

    void assertArtifact(String name, String extension, String type, String classifier) {
        def artifactName = metadata.artifactDefinitions*.artifactName.find({it.name == name})
        assert artifactName.name == name
        assert artifactName.type == type
        assert artifactName.extension == extension
        assert artifactName.classifier == classifier
    }

    def verifyFullDependencies(Collection<IvyDependencyDescriptor> dependencies) {
        // no conf def => equivalent to *->*
        def dd = getDependency(dependencies, "mymodule2")
        assert dd.selector == newSelector(DefaultModuleIdentifier.newId("myorg", "mymodule2"), new DefaultMutableVersionConstraint("2.0"))
        assert dd.confMappings == map("*": ["*"])
        assert !dd.changing
        assert dd.transitive
        assert dd.dependencyArtifacts.empty

        // changing, not transitive
        dd = getDependency(dependencies, "mymodule3")
        assert dd.changing
        assert !dd.transitive

        // conf="myconf1" => equivalent to myconf1->myconf1
        dd = getDependency(dependencies, "yourmodule1")
        assert dd.selector == newSelector(DefaultModuleIdentifier.newId("yourorg", "yourmodule1"), new DefaultMutableVersionConstraint("1.1"))
        assert dd.dynamicConstraintVersion == "1+"
        assert dd.confMappings == map(myconf1: ["myconf1"])
        assert dd.dependencyArtifacts.empty

        // conf="myconf1->yourconf1"
        dd = getDependency(dependencies, "yourmodule2")
        assert dd.selector == newSelector(DefaultModuleIdentifier.newId("yourorg", "yourmodule2"), new DefaultMutableVersionConstraint("2+"))
        assert dd.confMappings == map(myconf1: ["yourconf1"])
        assert dd.dependencyArtifacts.empty

        // conf="myconf1->yourconf1, yourconf2"
        dd = getDependency(dependencies, "yourmodule3")
        assert dd.selector == newSelector(DefaultModuleIdentifier.newId("yourorg", "yourmodule3"), new DefaultMutableVersionConstraint("3.1"))
        assert dd.confMappings == map(myconf1: ["yourconf1", "yourconf2"])
        assert dd.dependencyArtifacts.empty

        // conf="myconf1, myconf2->yourconf1, yourconf2"
        dd = getDependency(dependencies, "yourmodule4")
        assert dd.selector == newSelector(DefaultModuleIdentifier.newId("yourorg", "yourmodule4"), new DefaultMutableVersionConstraint("4.1"))
        assert dd.confMappings == map(myconf1:["yourconf1", "yourconf2"], myconf2:["yourconf1", "yourconf2"])
        assert dd.dependencyArtifacts.empty

        // conf="myconf1->yourconf1 | myconf2->yourconf1, yourconf2"
        dd = getDependency(dependencies, "yourmodule5")
        assert dd.selector == newSelector(DefaultModuleIdentifier.newId("yourorg", "yourmodule5"), new DefaultMutableVersionConstraint("5.1"))
        assert dd.confMappings == map(myconf1:["yourconf1"], myconf2:["yourconf1", "yourconf2"])
        assert dd.dependencyArtifacts.empty

        // conf="*->@"
        dd = getDependency(dependencies, "yourmodule11")
        assert dd.selector == newSelector(DefaultModuleIdentifier.newId("yourorg", "yourmodule11"), new DefaultMutableVersionConstraint("11.1"))
        assert dd.confMappings == map("*":["@"])
        assert dd.dependencyArtifacts.empty

        // Conf mappings as nested elements
        dd = getDependency(dependencies, "yourmodule6")
        assert dd.selector == newSelector(DefaultModuleIdentifier.newId("yourorg", "yourmodule6"), new DefaultMutableVersionConstraint("latest.integration"))
        assert dd.confMappings == map(myconf1:["yourconf1"], myconf2:["yourconf1", "yourconf2"])
        assert dd.dependencyArtifacts.empty

        // Conf mappings as deeply nested elements
        dd = getDependency(dependencies, "yourmodule7")
        assert dd.selector == newSelector(DefaultModuleIdentifier.newId("yourorg", "yourmodule7"), new DefaultMutableVersionConstraint("7.1"))
        assert dd.confMappings == map(myconf1:["yourconf1"], myconf2:["yourconf1", "yourconf2"])
        assert dd.dependencyArtifacts.empty

        // Dependency artifacts
        dd = getDependency(dependencies, "yourmodule8")
        assert dd.selector == newSelector(DefaultModuleIdentifier.newId("yourorg", "yourmodule8"), new DefaultMutableVersionConstraint("8.1"))
        assert dd.dependencyArtifacts.size() == 2
        assertDependencyArtifact(dd, "yourartifact8-1", ["myconf1", "myconf2", "myconf3", "myconf4", "myoldconf"])
        assertDependencyArtifact(dd, "yourartifact8-2", ["myconf1", "myconf2", "myconf3", "myconf4", "myoldconf"])

        // Dependency artifacts with confs
        dd = getDependency(dependencies, "yourmodule9")
        assert dd.selector == newSelector(DefaultModuleIdentifier.newId("yourorg", "yourmodule9"), new DefaultMutableVersionConstraint("9.1"))
        assert dd.dependencyArtifacts.size() == 2
        assertDependencyArtifact(dd, "yourartifact9-1", ["myconf1", "myconf2"])
        assertDependencyArtifact(dd, "yourartifact9-2", ["myconf2", "myconf3"])

        // Dependency excludes
        dd = getDependency(dependencies, "yourmodule10")
        assert dd.selector == newSelector(DefaultModuleIdentifier.newId("yourorg", "yourmodule10"), new DefaultMutableVersionConstraint("10.1"))
        assert dd.dependencyArtifacts.empty
        assert dd.allExcludes.size() == 1
        assert dd.allExcludes[0].artifact.name == "toexclude"
        assert dd.allExcludes[0].configurations as Set == ["myconf1", "myconf2", "myconf3", "myconf4", "myoldconf"] as Set
        true
    }

    protected SetMultimap<String, String> map(Map<String, List<String>> map) {
        SetMultimap<String, String> result = LinkedHashMultimap.create()
        for (Map.Entry<String, List<String>> entry : map.entrySet()) {
            result.putAll(entry.key, entry.value)
        }
        return result
    }

    protected void assertArtifacts(String configuration, List<String> artifactNames) {
        def configurationArtifactNames = artifacts(configuration)*.artifactName*.name
        assert configurationArtifactNames as Set == artifactNames as Set
    }

    protected void assertConf(String name, String desc, boolean visible, String[] exts) {
        def conf = metadata.configurationDefinitions[name]
        assert conf != null : "configuration not found: " + name
        assert conf.name == name
        assert conf.visible == visible
        assert conf.extendsFrom as Set == exts as Set
    }

    protected static IvyDependencyDescriptor getDependency(Collection<IvyDependencyDescriptor> dependencies, String name) {
        def found = dependencies.find { it.selector.module == name }
        assert found != null
        return found
    }

    protected static void assertDependencyArtifact(IvyDependencyDescriptor dd, String name, List<String> confs) {
        def artifact = dd.dependencyArtifacts.find { it.artifactName.name == name }
        assert artifact != null
        assert artifact.configurations == confs as Set
    }
}
