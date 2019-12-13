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

import org.gradle.api.InvalidUserDataException
import org.gradle.api.internal.artifacts.verification.DependencyVerifier
import org.gradle.api.internal.artifacts.verification.model.ChecksumKind
import spock.lang.Specification
import spock.lang.Unroll

class DependencyVerificationsXmlReaderTest extends Specification {
    private DependencyVerifier verifier

    def "reasonable error message when format is invalid"() {
        when:
        parse("invalid")

        then:
        InvalidUserDataException e = thrown()
        e.message == "Unable to read dependency verification metadata"
    }

    def "reasonable error message when XML doesn't have the expected structure"() {
        when:
        parse """<?xml version="1.0" encoding="UTF-8"?>
<verification-metadata>
   <component/>
</verification-metadata>"""

        then:
        InvalidUserDataException e = thrown()
        e.message == "Unable to read dependency verification metadata"
        e.cause.message == "Invalid dependency verification metadata file: <component> must be found under the <components> tag"
    }

    @Unroll
    def "parses configuration"() {
        when:
        parse """<?xml version="1.0" encoding="UTF-8"?>
<verification-metadata>
   <configuration>
      <verify-metadata>$verifyMetadata</verify-metadata>
   </configuration>
</verification-metadata>
"""
        then:
        verifier.configuration.verifyMetadata == verifyMetadata

        where:
        verifyMetadata << [true, false]
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

    @Unroll
    def "reasonable error message when trusted artifact notation is incorrect (#notation)"() {
        when:
        parse """<?xml version="1.0" encoding="UTF-8"?>
<verification-metadata>
   <configuration>
      <verify-metadata>true</verify-metadata>
      <trusted-artifacts>
         <trust $notation/>
      </trusted-artifacts>
   </configuration>
</verification-metadata>
"""
        then:
        InvalidUserDataException ex = thrown()
        ex.message == "Unable to read dependency verification metadata"
        ex.cause.message == error

        where:
        notation                                  | error
        'name="name"'                             | "Invalid dependency verification metadata file: Missing attribute: group"
        'group="foo" version="1.0"'               | "name cannot be null"
        'group="foo" file="file.jar"'             | "name cannot be null"
        'group="foo" name="name" file="file.jar"' | "version cannot be null"
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
        first.componentId.module == "foo"
        first.componentId.version == "1.0"
        first.artifactVerifications.size() == 1
        first.artifactVerifications[0].checksums.find { it.kind == ChecksumKind.sha1 }.value == "abc"
        first.artifactVerifications[0].checksums.find { it.kind == ChecksumKind.sha256 }.value == "bcd"

        def second = verifier.verificationMetadata[1]
        second.componentId.group == "org"
        second.componentId.module == "foo"
        second.componentId.version == "1.1"
        second.artifactVerifications.size() == 2
        second.artifactVerifications[0].artifactName == "foo-1.1.jar"
        second.artifactVerifications[0].checksums.find { it.kind == ChecksumKind.md5 }.value == "1234"
        second.artifactVerifications[1].artifactName == "foo-1.1.zip"
        second.artifactVerifications[1].checksums.find { it.kind == ChecksumKind.sha1 }.value == "5678"

        def third = verifier.verificationMetadata[2]
        third.componentId.group == "org"
        third.componentId.module == "bar"
        third.componentId.version == "1.2"
        third.artifactVerifications.size() == 2
        third.artifactVerifications[0].artifactName == "bar-1.2.jar"
        third.artifactVerifications[0].checksums.find { it.kind == ChecksumKind.sha1 }.value == "9876"
        third.artifactVerifications[0].checksums.find { it.kind == ChecksumKind.sha512 }.value == "123def"
        third.artifactVerifications[1].artifactName == "bar-1.2-classy.jar"
        third.artifactVerifications[1].checksums.find { it.kind == ChecksumKind.sha512 }.value == "5678abcd"

        def fourth = verifier.verificationMetadata[3]
        fourth.componentId.group == "org"
        fourth.componentId.module == "baz"
        fourth.componentId.version == "1.4"
        fourth.artifactVerifications.size() == 1
        fourth.artifactVerifications[0].artifactName == "baz-1.4.jar"
        def firstChecksum = fourth.artifactVerifications[0].checksums.find { it.kind == ChecksumKind.sha1 }
        firstChecksum.value == "9876"
        firstChecksum.alternatives == null
        firstChecksum.origin == "Generated by Gradle"

        def secondChecksum = fourth.artifactVerifications[0].checksums.find { it.kind == ChecksumKind.md5 }
        secondChecksum.value == "123def"
        secondChecksum.alternatives == ["ok", "computer"] as Set
        secondChecksum.origin == null
    }

    void parse(String xml) {
        verifier = DependencyVerificationsXmlReader.readFromXml(
            new ByteArrayInputStream(xml.getBytes("utf-8"))
        )
    }
}
