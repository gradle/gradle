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


import org.gradle.api.internal.artifacts.verification.exceptions.DependencyVerificationException
import org.gradle.api.internal.artifacts.verification.model.ChecksumKind
import org.gradle.api.internal.artifacts.verification.model.IgnoredKey
import org.gradle.api.internal.artifacts.verification.verifier.DependencyVerifier
import spock.lang.Specification

class DependencyVerificationsXmlReaderTest extends Specification {
    private DependencyVerifier verifier

    def "reasonable error message when format is invalid"() {
        when:
        parse("invalid")

        then:
        DependencyVerificationException e = thrown()
        e.message == "Unable to read dependency verification metadata"
    }

    def "reasonable error message when external xml entities are used"() {

        when:
        parse """<?xml version="1.0" encoding="UTF-8"?>
        <!DOCTYPE data [
          <!ENTITY file SYSTEM "file://external.txt">
        ]>
        <verification-metadata>
            &file;
        </verification-metadata>
        """

        then:
        DependencyVerificationException e = thrown()
        e.message == "Unable to read dependency verification metadata"
        e.cause.message.contains("DOCTYPE is disallowed")
    }

    def "can parse file with namespace declaration"() {
        when:
        parse """<?xml version="1.0" encoding="UTF-8"?>
<verification-metadata xmlns="https://schema.gradle.org/dependency-verification"
      xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
      xsi:schemaLocation="https://schema.gradle.org/dependency-verification https://schema.gradle.org/dependency-verification/dependency-verification-1.2.xsd">
</verification-metadata>
"""
        then:
        noExceptionThrown()
    }

    def "reasonable error message when XML doesn't have the expected structure"() {
        when:
        parse """<?xml version="1.0" encoding="UTF-8"?>
<verification-metadata>
   <component/>
</verification-metadata>"""

        then:
        DependencyVerificationException e = thrown()
        e.message == "Unable to read dependency verification metadata"
        e.cause.message == "Invalid dependency verification metadata file: <component> must be found under the <components> tag"
    }

    def "parses configuration (metadata=#verifyMetadata, signatures=#verifySignatures)"() {
        when:
        parse """<?xml version="1.0" encoding="UTF-8"?>
<verification-metadata>
   <configuration>
      <verify-metadata>$verifyMetadata</verify-metadata>
      <verify-signatures>$verifySignatures</verify-signatures>
   </configuration>
</verification-metadata>
"""
        then:
        verifier.configuration.verifyMetadata == verifyMetadata
        verifier.configuration.verifySignatures == verifySignatures
        verifier.configuration.keyServers == []

        where:
        verifyMetadata | verifySignatures
        false          | false
        false          | true
        true           | false
        true           | true

    }

    def "parses key servers"() {
        when:
        parse """<?xml version="1.0" encoding="UTF-8"?>
<verification-metadata>
   <configuration>
      <key-servers>
         <key-server uri="https://pgp.key-server.io"/>
         <key-server uri="hkp://keys.openpgp.org"/>
      </key-servers>
   </configuration>
</verification-metadata>
"""
        then:
        verifier.configuration.keyServers == [
            new URI("https://pgp.key-server.io"),
            new URI("hkp://keys.openpgp.org")
        ]
    }
    def "parses trusted artifacts"() {
        when:
        parse """<?xml version="1.0" encoding="UTF-8"?>
<verification-metadata>
   <configuration>
      <verify-metadata>true</verify-metadata>
      <trusted-artifacts>
         <trust group="group"/>
         <trust group="group" name="module"/>
         <trust group="group" name="module" version="1.0" regex="true"/>
         <trust group="group" name="module" version="1.1" file="somefile.jar"/>
         <trust group="group2" name="module2" version="1.2" file="somefile.jar" regex="true"/>
      </trusted-artifacts>
   </configuration>
</verification-metadata>
"""
        then:
        verifier.configuration.verifyMetadata
        def trusted = verifier.configuration.trustedArtifacts
        trusted.size() == 5
        trusted[0].group == "group"
        trusted[0].name == null
        trusted[0].version == null
        trusted[0].fileName == null
        trusted[0].regex == false

        trusted[1].group == "group"
        trusted[1].name == "module"
        trusted[1].version == null
        trusted[1].fileName == null
        trusted[1].regex == false

        trusted[2].group == "group"
        trusted[2].name == "module"
        trusted[2].version == "1.0"
        trusted[2].fileName == null
        trusted[2].regex == true

        trusted[3].group == "group"
        trusted[3].name == "module"
        trusted[3].version == "1.1"
        trusted[3].fileName == "somefile.jar"
        trusted[3].regex == false

        trusted[4].group == "group2"
        trusted[4].name == "module2"
        trusted[4].version == "1.2"
        trusted[4].fileName == "somefile.jar"
        trusted[4].regex == true
    }

    def "reasonable error message when trusted artifact isn't well defined"() {
        when:
        parse """<?xml version="1.0" encoding="UTF-8"?>
<verification-metadata>
   <configuration>
      <verify-metadata>true</verify-metadata>
      <trusted-artifacts>
         <trust/>
      </trusted-artifacts>
   </configuration>
</verification-metadata>
"""
        then:
        DependencyVerificationException ex = thrown()
        ex.message == "Unable to read dependency verification metadata"
        ex.cause.message == "A trusted artifact must have at least one of group, name, version or file name not null"
    }

    def "can parse ignored keys"() {
        when:
        parse """<?xml version="1.0" encoding="UTF-8"?>
<verification-metadata>
   <configuration>
      <verify-metadata>true</verify-metadata>
      <ignored-keys>
         <ignored-key id="ABCDEF"/>
         <ignored-key id="012345" reason="nope"/>
      </ignored-keys>
   </configuration>
</verification-metadata>
"""
        then:
        verifier.configuration.ignoredKeys == [key("ABCDEF"), key("012345", "nope")] as Set
    }

    def "can parse keyring format"() {
        when:
        parse """<?xml version="1.0" encoding="UTF-8"?>
<verification-metadata>
   <configuration>
      <verify-metadata>true</verify-metadata>
      <keyring-format>text</keyring-format>
   </configuration>
</verification-metadata>
"""
        then:
        verifier.configuration.keyRingFormat == "text"
    }

    def "reasonable error message when invalid invalid keyring format given"() {
        when:
        parse """<?xml version="1.0" encoding="UTF-8"?>
<verification-metadata>
   <configuration>
      <verify-metadata>true</verify-metadata>
      <keyring-format>invalid_format</keyring-format>
   </configuration>
</verification-metadata>
"""
        then:
        DependencyVerificationException e = thrown()
        e.message == "Unable to read dependency verification metadata"
        e.cause.message == "Invalid key ring format: The key ring format should be either 'text' or 'gpg', which determine how keys are stored. Please choose a valid format or leave it unset to generate both."
    }

    def "can parse trusted keys"() {
        when:
        parse """<?xml version="1.0" encoding="UTF-8"?>
<verification-metadata>
   <configuration>
      <verify-metadata>true</verify-metadata>
      <verify-signatures>false</verify-signatures>
      <trusted-keys>
         <trusted-key id="A000000000000000000000000000000000000000" group="g2" name="m1" file="file.jar" regex="true"/>
         <trusted-key id="B000000000000000000000000000000000000000">
            <trusting name="m3" version="1.4" file="file.zip"/>
            <trusting name="m4" file="other-file.zip" regex="true"/>
         </trusted-key>
         <trusted-key id="C000000000000000000000000000000000000000" group="g3" name="m2" version="1.0" regex="true"/>
         <trusted-key id="D000000000000000000000000000000000000000" group="g1"/>
      </trusted-keys>
   </configuration>
   <components/>
</verification-metadata>
"""

        then:
        def trustedKeys = verifier.configuration.trustedKeys
        trustedKeys.size() == 5

        trustedKeys[0].keyId == "A000000000000000000000000000000000000000"
        trustedKeys[0].group == "g2"
        trustedKeys[0].name == "m1"
        trustedKeys[0].version == null
        trustedKeys[0].fileName == "file.jar"
        trustedKeys[0].regex == true

        trustedKeys[1].keyId == "B000000000000000000000000000000000000000"
        trustedKeys[1].group == null
        trustedKeys[1].name == "m3"
        trustedKeys[1].version == "1.4"
        trustedKeys[1].fileName == "file.zip"
        trustedKeys[1].regex == false

        trustedKeys[2].keyId == "B000000000000000000000000000000000000000"
        trustedKeys[2].group == null
        trustedKeys[2].name == "m4"
        trustedKeys[2].version == null
        trustedKeys[2].fileName == "other-file.zip"
        trustedKeys[2].regex == true

        trustedKeys[3].keyId == "C000000000000000000000000000000000000000"
        trustedKeys[3].group == "g3"
        trustedKeys[3].name == "m2"
        trustedKeys[3].version == "1.0"
        trustedKeys[3].fileName == null
        trustedKeys[3].regex == true

        trustedKeys[4].keyId == "D000000000000000000000000000000000000000"
        trustedKeys[4].group == "g1"
        trustedKeys[4].name == null
        trustedKeys[4].version == null
        trustedKeys[4].fileName == null
        trustedKeys[4].regex == false


    }

    def "can parse dependency verification metadata"() {
        when:
        parse """<?xml version="1.0" encoding="UTF-8"?>
<verification-metadata>
   <components>
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
         <artifact name="foo-1.1.zip">
            <sha1 value="5678"/>
         </artifact>
      </component>
      <component group="org" name="bar" version="1.2">
         <artifact name="bar-1.2.jar">
            <sha1 value="9876"/>
            <sha512 value="123def"/>
         </artifact>
         <artifact name="bar-1.2-classy.jar">
            <sha512 value="5678abcd"/>
         </artifact>
      </component>
      <component group="org" name="baz" version="1.4">
         <artifact name="baz-1.4.jar">
            <sha1 value="9876" origin="Generated by Gradle"/>
            <md5 value="123def">
                <also-trust value="ok"/>
                <also-trust value="computer"/>
            </md5>
         </artifact>
      </component>
   </components>
</verification-metadata>
"""
        then:
        verifier.verificationMetadata.size() == 4

        def first = verifier.verificationMetadata[0]
        first.componentId.group == "org"
        first.componentId.module == "bar"
        first.componentId.version == "1.2"
        first.artifactVerifications.size() == 2
        first.artifactVerifications[0].artifactName == "bar-1.2-classy.jar"
        first.artifactVerifications[0].checksums.find { it.kind == ChecksumKind.sha512 }.value == "5678abcd"
        first.artifactVerifications[1].artifactName == "bar-1.2.jar"
        first.artifactVerifications[1].checksums.find { it.kind == ChecksumKind.sha1 }.value == "9876"
        first.artifactVerifications[1].checksums.find { it.kind == ChecksumKind.sha512 }.value == "123def"

        def second = verifier.verificationMetadata[1]
        second.componentId.group == "org"
        second.componentId.module == "baz"
        second.componentId.version == "1.4"
        second.artifactVerifications.size() == 1
        second.artifactVerifications[0].artifactName == "baz-1.4.jar"

        def firstChecksum = second.artifactVerifications[0].checksums.find { it.kind == ChecksumKind.sha1 }
        firstChecksum.value == "9876"
        firstChecksum.alternatives == null
        firstChecksum.origin == "Generated by Gradle"

        def secondChecksum = second.artifactVerifications[0].checksums.find { it.kind == ChecksumKind.md5 }
        secondChecksum.value == "123def"
        secondChecksum.alternatives == ["ok", "computer"] as Set
        secondChecksum.origin == null

        def third = verifier.verificationMetadata[2]
        third.componentId.group == "org"
        third.componentId.module == "foo"
        third.componentId.version == "1.0"
        third.artifactVerifications.size() == 1
        third.artifactVerifications[0].checksums.find { it.kind == ChecksumKind.sha1 }.value == "abc"
        third.artifactVerifications[0].checksums.find { it.kind == ChecksumKind.sha256 }.value == "bcd"

        def fourth = verifier.verificationMetadata[3]
        fourth.componentId.group == "org"
        fourth.componentId.module == "foo"
        fourth.componentId.version == "1.1"
        fourth.artifactVerifications.size() == 2
        fourth.artifactVerifications[0].artifactName == "foo-1.1.jar"
        fourth.artifactVerifications[0].checksums.find { it.kind == ChecksumKind.md5 }.value == "1234"
        fourth.artifactVerifications[1].artifactName == "foo-1.1.zip"
        fourth.artifactVerifications[1].checksums.find { it.kind == ChecksumKind.sha1 }.value == "5678"

    }

    def "can parse artifact specific ignored keys"() {
        when:
        parse """<?xml version="1.0" encoding="UTF-8"?>
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
               <ignored-key id="123"/>
               <ignored-key id="456" reason="bad things happen"/>
            </ignored-keys>
         </artifact>
      </component>
   </components>
</verification-metadata>"""

        then:
        def component = verifier.verificationMetadata[0]
        def artifacts = component.artifactVerifications
        artifacts[0].ignoredPgpKeys == [key('ABC')] as Set
        artifacts[1].ignoredPgpKeys == [key('123'), key('456', 'bad things happen')] as Set
    }

    private static IgnoredKey key(String id, String reason = null) {
        new IgnoredKey(id, reason)
    }

    void parse(String xml) {
        verifier = DependencyVerificationsXmlReader.readFromXml(
            new ByteArrayInputStream(xml.getBytes("utf-8"))
        )
    }
}
