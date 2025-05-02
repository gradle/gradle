/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.internal.artifacts.verification.serializer

import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.verification.model.ChecksumKind
import org.gradle.api.internal.artifacts.verification.model.IgnoredKey
import org.gradle.api.internal.artifacts.verification.verifier.DependencyVerifierBuilder
import org.gradle.internal.component.external.model.DefaultModuleComponentArtifactIdentifier
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.external.model.ModuleComponentFileArtifactIdentifier
import org.gradle.util.internal.TextUtil
import org.xml.sax.SAXException
import spock.lang.Issue
import spock.lang.Specification

import javax.xml.XMLConstants
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.Schema
import javax.xml.validation.SchemaFactory

class DependencyVerificationsXmlWriterTest extends Specification {
    private final DependencyVerifierBuilder builder = new DependencyVerifierBuilder()
    private String rawContents
    private String contents

    private Map<String, Schema> schemas = loadSchemas()

    def "can write an empty file"() {
        when:
        builder.verifyMetadata = verifyMetadata
        builder.verifySignatures = verifySignatures
        serialize()

        then:
        contents == """<?xml version="1.0" encoding="UTF-8"?>
<verification-metadata>
   <configuration>
      <verify-metadata>$verifyMetadata</verify-metadata>
      <verify-signatures>$verifySignatures</verify-signatures>
   </configuration>
   <components/>
</verification-metadata>
"""
        and:
        hasNamespaceDeclaration()

        and:
        validateAgainstSchemasSince("1.3")

        where:
        verifyMetadata | verifySignatures
        false          | false
        false          | true
        true           | false
        true           | true
    }

    def 'can write top level comments'() {
        when:
        builder.addTopLevelComment("Some top level comment")
        builder.addTopLevelComment("Another comment\non two lines")
        serialize()

        then:
        contents == """<?xml version="1.0" encoding="UTF-8"?>
<!-- Some top level comment -->
<!-- Another comment
on two lines -->
<verification-metadata>
   <configuration>
      <verify-metadata>true</verify-metadata>
      <verify-signatures>false</verify-signatures>
   </configuration>
   <components/>
</verification-metadata>
"""
        and:
        hasNamespaceDeclaration()

        and:
        validateAgainstSchemasSince("1.3")
    }

    def "can declare key servers"() {
        when:
        builder.addKeyServer(new URI("https://pgp.key-server.io"))
        builder.addKeyServer(new URI("hkp://keys.openpgp.org"))
        serialize()

        then:
        contents == """<?xml version="1.0" encoding="UTF-8"?>
<verification-metadata>
   <configuration>
      <verify-metadata>true</verify-metadata>
      <verify-signatures>false</verify-signatures>
      <key-servers>
         <key-server uri="https://pgp.key-server.io"/>
         <key-server uri="hkp://keys.openpgp.org"/>
      </key-servers>
   </configuration>
   <components/>
</verification-metadata>
"""
        and:
        validateAgainstSchemasSince("1.3")
    }


    def "can declare trusted artifacts"() {
        when:
        builder.with {
            addTrustedArtifact("group", null, null, null, false)
            addTrustedArtifact("group", "module", null, null, false)
            addTrustedArtifact("group", "module", "1.0", null, true)
            addTrustedArtifact("group", "module", "1.1", "somefile.jar", false)
            addTrustedArtifact("group2", "module2", "1.2", "somefile.jar", true)
            addTrustedArtifact(null, "module2", null, "somefile.jar", true)
        }
        serialize()

        then:
        contents == """<?xml version="1.0" encoding="UTF-8"?>
<verification-metadata>
   <configuration>
      <verify-metadata>true</verify-metadata>
      <verify-signatures>false</verify-signatures>
      <trusted-artifacts>
         <trust group="group"/>
         <trust group="group" name="module"/>
         <trust group="group" name="module" version="1.1" file="somefile.jar"/>
         <trust name="module2" file="somefile.jar" regex="true"/>
         <trust group="group" name="module" version="1.0" regex="true"/>
         <trust group="group2" name="module2" version="1.2" file="somefile.jar" regex="true"/>
      </trusted-artifacts>
   </configuration>
   <components/>
</verification-metadata>
"""
        and:
        validateAgainstSchemasSince("1.3")
    }

    @Issue("https://github.com/gradle/gradle/issues/27891")
    def "can declare and sorts ignored keys"() {
        when:
        builder.addIgnoredKey(new IgnoredKey("ABCDEF", null))
        builder.addIgnoredKey(new IgnoredKey("012345", "test"))
        serialize()

        then:
        contents == """<?xml version="1.0" encoding="UTF-8"?>
<verification-metadata>
   <configuration>
      <verify-metadata>true</verify-metadata>
      <verify-signatures>false</verify-signatures>
      <ignored-keys>
         <ignored-key id="012345" reason="test"/>
         <ignored-key id="ABCDEF"/>
      </ignored-keys>
   </configuration>
   <components/>
</verification-metadata>
"""
        and:
        validateAgainstSchemasSince("1.3")
    }

    def "can declare trusted keys"() {
        when:
        builder.addTrustedKey("A000000000000000000000000000000000000000", "g1", null, null, null, false)
        builder.addTrustedKey("B000000000000000000000000000000000000000", "g2", "m1", null, "file.jar", true)
        builder.addTrustedKey("C000000000000000000000000000000000000000", "g3", "m2", "1.0", null, true)
        builder.addTrustedKey("D000000000000000000000000000000000000000", null, "m3", "1.4", "file.zip", false)
        builder.addTrustedKey("D000000000000000000000000000000000000000", null, "m4", null, "other-file.zip", true)
        serialize()

        then:
        contents == """<?xml version="1.0" encoding="UTF-8"?>
<verification-metadata>
   <configuration>
      <verify-metadata>true</verify-metadata>
      <verify-signatures>false</verify-signatures>
      <trusted-keys>
         <trusted-key id="A000000000000000000000000000000000000000" group="g1"/>
         <trusted-key id="B000000000000000000000000000000000000000" group="g2" name="m1" file="file.jar" regex="true"/>
         <trusted-key id="C000000000000000000000000000000000000000" group="g3" name="m2" version="1.0" regex="true"/>
         <trusted-key id="D000000000000000000000000000000000000000">
            <trusting name="m3" version="1.4" file="file.zip"/>
            <trusting name="m4" file="other-file.zip" regex="true"/>
         </trusted-key>
      </trusted-keys>
   </configuration>
   <components/>
</verification-metadata>
"""
        and:
        validateAgainstSchemasSince("1.3")
    }

    def "can declare ignored keys for specific artifact"() {
        when:
        addIgnoredKeyForArtifact("org:foo:1.0", "foo-1.0.jar", "ABC")
        addIgnoredKeyForArtifact("org:foo:1.0", "foo-1.0.pom", "123", "so wrong!")
        serialize()

        then:
        contents == """<?xml version="1.0" encoding="UTF-8"?>
<verification-metadata>
   <configuration>
      <verify-metadata>true</verify-metadata>
      <verify-signatures>false</verify-signatures>
   </configuration>
   <components>
      <component group="org" name="foo" version="1.0">
         <artifact name="foo-1.0.jar">
            <ignored-keys>
               <ignored-key id="ABC"/>
            </ignored-keys>
         </artifact>
         <artifact name="foo-1.0.pom">
            <ignored-keys>
               <ignored-key id="123" reason="so wrong!"/>
            </ignored-keys>
         </artifact>
      </component>
   </components>
</verification-metadata>
"""
        and:
        validateAgainstSchemasSince("1.3")
    }

    def "can declare keyring format"() {
        when:
        builder.setKeyringFormat("armored")
        serialize()

        then:
        contents == """<?xml version="1.0" encoding="UTF-8"?>
<verification-metadata>
   <configuration>
      <verify-metadata>true</verify-metadata>
      <verify-signatures>false</verify-signatures>
      <keyring-format>armored</keyring-format>
   </configuration>
   <components/>
</verification-metadata>
"""
        and:
        validateAgainstSchemasSince("1.3")
    }

    def "entries are sorted"() {
        given:
        declareChecksum("org:foo:1.0", "sha1", "abc")
        declareChecksum("org:foo:1.0", "sha256", "bcd")
        declareChecksum("org:foo:1.1", "md5", "1234")
        declareChecksum("org:bar:1.2", "sha512", "123def")
        declareChecksum("org:bar:1.2", "sha1", "9876")

        when:
        serialize()

        then:
        contents == """<?xml version="1.0" encoding="UTF-8"?>
<verification-metadata>
   <configuration>
      <verify-metadata>true</verify-metadata>
      <verify-signatures>false</verify-signatures>
   </configuration>
   <components>
      <component group="org" name="bar" version="1.2">
         <artifact name="bar-1.2.jar">
            <sha1 value="9876"/>
            <sha512 value="123def"/>
         </artifact>
      </component>
      <component group="org" name="foo" version="1.0">
         <artifact name="foo-1.0.jar">
            <sha1 value="abc"/>
            <sha256 value="bcd"/>
         </artifact>
      </component>
      <component group="org" name="foo" version="1.1">
         <artifact name="foo-1.1.jar">
            <md5 value="1234"/>
         </artifact>
      </component>
   </components>
</verification-metadata>
"""
        and:
        validateAgainstSchemasSince("1.3")
    }

    def "can declare checksums for secondary artifacts"() {
        given:
        declareChecksum("org:foo:1.0", "sha1", "abc")
        declareChecksumOfArtifact("org", "foo", "1.0", "zip", "zip", null, "sha256", "def")
        declareChecksumOfArtifact("org", "foo", "1.0", "jar", "jar", "classy", "sha1", "123")

        when:
        serialize()

        then:
        contents == """<?xml version="1.0" encoding="UTF-8"?>
<verification-metadata>
   <configuration>
      <verify-metadata>true</verify-metadata>
      <verify-signatures>false</verify-signatures>
   </configuration>
   <components>
      <component group="org" name="foo" version="1.0">
         <artifact name="foo-1.0-classy.jar">
            <sha1 value="123"/>
         </artifact>
         <artifact name="foo-1.0.jar">
            <sha1 value="abc"/>
         </artifact>
         <artifact name="foo-1.0.zip">
            <sha256 value="def"/>
         </artifact>
      </component>
   </components>
</verification-metadata>
"""
        and:
        validateAgainstSchemasSince("1.3")
    }

    void "can declare origin of a checksum"() {
        declareChecksum("org:foo:1.0", "sha1", "abc", "from test")
        declareChecksum("org:bar:1.0", "sha1", "abc", "from test")
        declareChecksum("org:bar:1.0", "md5", "abc", "other")

        when:
        serialize()

        then:
        contents == """<?xml version="1.0" encoding="UTF-8"?>
<verification-metadata>
   <configuration>
      <verify-metadata>true</verify-metadata>
      <verify-signatures>false</verify-signatures>
   </configuration>
   <components>
      <component group="org" name="bar" version="1.0">
         <artifact name="bar-1.0.jar">
            <md5 value="abc" origin="other"/>
            <sha1 value="abc" origin="from test"/>
         </artifact>
      </component>
      <component group="org" name="foo" version="1.0">
         <artifact name="foo-1.0.jar">
            <sha1 value="abc" origin="from test"/>
         </artifact>
      </component>
   </components>
</verification-metadata>
"""
        and:
        validateAgainstSchemasSince("1.3")
    }

    void "can declare more than one checksum of the same kind"() {
        declareChecksum("org:foo:1.0", "sha1", "abc")
        declareChecksum("org:foo:1.0", "sha1", "def")
        declareChecksum("org:foo:1.0", "sha1", "123")

        when:
        serialize()

        then:
        contents == """<?xml version="1.0" encoding="UTF-8"?>
<verification-metadata>
   <configuration>
      <verify-metadata>true</verify-metadata>
      <verify-signatures>false</verify-signatures>
   </configuration>
   <components>
      <component group="org" name="foo" version="1.0">
         <artifact name="foo-1.0.jar">
            <sha1 value="abc">
               <also-trust value="def"/>
               <also-trust value="123"/>
            </sha1>
         </artifact>
      </component>
   </components>
</verification-metadata>
"""
        and:
        validateAgainstSchemasSince("1.3")
    }

    private boolean hasNamespaceDeclaration() {
        rawContents.contains('<verification-metadata xmlns="https://schema.gradle.org/dependency-verification" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="https://schema.gradle.org/dependency-verification https://schema.gradle.org/dependency-verification/dependency-verification-1.3.xsd"')
    }

    private void addIgnoredKeyForArtifact(String id, String fileName, String key, String reason = null) {
        def (group, name, version) = id.split(":")
        builder.addIgnoredKey(
            new ModuleComponentFileArtifactIdentifier(
                DefaultModuleComponentIdentifier.newId(
                    DefaultModuleIdentifier.newId(group, name),
                    version
                ),
                fileName
            ),
            new IgnoredKey(key, reason)
        )
    }

    private void declareChecksum(String id, String algorithm, String checksum, String origin = null) {
        def (group, name, version) = id.split(":")
        declareChecksumOfArtifact(group, name, version, "jar", "jar", null, algorithm, checksum, origin)
    }

    private declareChecksumOfArtifact(String group, String name, version, String type, String ext, String classifier, String algorithm, String checksum, String origin = null, String reason = null) {
        builder.addChecksum(
            new DefaultModuleComponentArtifactIdentifier(
                DefaultModuleComponentIdentifier.newId(
                    DefaultModuleIdentifier.newId(group, name),
                    version
                ),
                name,
                type,
                ext,
                classifier
            ),
            ChecksumKind.valueOf(algorithm),
            checksum,
            origin,
            reason
        )
    }

    private void serialize() {
        def out = new ByteArrayOutputStream()
        DependencyVerificationsXmlWriter.serialize(builder.build(), out)
        rawContents = TextUtil.normaliseLineSeparators(out.toString("utf-8"))
        contents = rawContents.replaceAll("<verification-metadata .+>", "<verification-metadata>")
    }

    private Map<String, Schema> loadSchemas() {
        // NOTE: it would be nice to load all schemas from the classpath, but 1.0, 1.1 and 1.2 are invalid
        def versions = ["1.3"]
        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);

        def result = versions.collectEntries { version ->
            def schemaResource = DependencyVerificationsXmlWriter.getResourceAsStream("/org/gradle/schema/dependency-verification-${version}.xsd")
            def schema
            try {
                schema = factory.newSchema(new StreamSource(schemaResource))
            } catch (Throwable exception) {
                throw new RuntimeException("Could not load schema for version ${version}", exception)
            }
            [version, schema]
        }
        assert result.size() > 0: "No schemas found"
        result
    }

    private void validateAgainstSchemasSince(String version) {
        validAgainstSchemas(schemas.keySet().findAll { it >= version })
    }

    private void validAgainstSchemas(Collection<String> versions) {
        def invalid = []
        versions.each {
            def schema = schemas[it]
            def validator = schema.newValidator()
            def source = new StreamSource(new StringReader(rawContents))
            try {
                validator.validate(source)
            } catch (SAXException e) {
                invalid.add(it)
                println("Exception validating against schema $it: $e")
            }
        }
        assert invalid.isEmpty(): "Invalid against schemas: $invalid"
    }
}
