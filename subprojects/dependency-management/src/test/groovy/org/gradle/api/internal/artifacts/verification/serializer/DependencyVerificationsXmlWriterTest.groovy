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
import org.gradle.integtests.tooling.fixture.TextUtil
import org.gradle.internal.component.external.model.DefaultModuleComponentArtifactIdentifier
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.external.model.ModuleComponentFileArtifactIdentifier
import spock.lang.Specification
import spock.lang.Unroll

class DependencyVerificationsXmlWriterTest extends Specification {
    private final DependencyVerifierBuilder builder = new DependencyVerifierBuilder()
    private String rawContents
    private String contents

    @Unroll
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

        where:
        verifyMetadata | verifySignatures
        false          | false
        false          | true
        true           | false
        true           | true
    }

    private boolean hasNamespaceDeclaration() {
        rawContents.contains('<verification-metadata xmlns="https://schema.gradle.org/verification" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:schemaLocation="https://schema.gradle.org/verification https://schema.gradle.org/verification/verification-1.0.xsd"')
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
         <trust group="group" name="module" version="1.0" regex="true"/>
         <trust group="group" name="module" version="1.1" file="somefile.jar"/>
         <trust group="group2" name="module2" version="1.2" file="somefile.jar" regex="true"/>
         <trust name="module2" file="somefile.jar" regex="true"/>
      </trusted-artifacts>
   </configuration>
   <components/>
</verification-metadata>
"""
    }

    def "can declare ignored keys"() {
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
         <ignored-key id="ABCDEF"/>
         <ignored-key id="012345" reason="test"/>
      </ignored-keys>
   </configuration>
   <components/>
</verification-metadata>
"""
    }

    def "can declare trusted keys"() {
        when:
        builder.addTrustedKey("ABCDEF", "g1", null, null, null, false)
        builder.addTrustedKey("012345", "g2", "m1", null, "file.jar", true)
        builder.addTrustedKey("ABC123", "g3", "m2", "1.0", null, true)
        builder.addTrustedKey("456DEF", null, "m3", "1.4", "file.zip", false)
        builder.addTrustedKey("456DEF", null, "m4", null, "other-file.zip", true)
        serialize()

        then:
        contents == """<?xml version="1.0" encoding="UTF-8"?>
<verification-metadata>
   <configuration>
      <verify-metadata>true</verify-metadata>
      <verify-signatures>false</verify-signatures>
      <trusted-keys>
         <trusted-key id="012345" group="g2" name="m1" file="file.jar" regex="true"/>
         <trusted-key id="456DEF">
            <trusting name="m3" version="1.4" file="file.zip"/>
            <trusting name="m4" file="other-file.zip" regex="true"/>
         </trusted-key>
         <trusted-key id="ABC123" group="g3" name="m2" version="1.0" regex="true"/>
         <trusted-key id="ABCDEF" group="g1"/>
      </trusted-keys>
   </configuration>
   <components/>
</verification-metadata>
"""
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
    }

    void addIgnoredKeyForArtifact(String id, String fileName, String key, String reason = null) {
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

    void declareChecksum(String id, String algorithm, String checksum, String origin = null) {
        def (group, name, version) = id.split(":")
        declareChecksumOfArtifact(group, name, version, "jar", "jar", null, algorithm, checksum, origin)
    }

    private declareChecksumOfArtifact(String group, String name, version, String type, String ext, String classifier, String algorithm, String checksum, String origin = null) {
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
            origin
        )
    }

    private void serialize() {
        def out = new ByteArrayOutputStream()
        DependencyVerificationsXmlWriter.serialize(builder.build(), out)
        rawContents = TextUtil.normaliseLineSeparators(out.toString("utf-8"))
        contents = rawContents.replaceAll("<verification-metadata .+>", "<verification-metadata>")
    }
}
