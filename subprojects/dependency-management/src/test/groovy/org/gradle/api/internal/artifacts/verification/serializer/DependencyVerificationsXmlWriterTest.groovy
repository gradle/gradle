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
import org.gradle.api.internal.artifacts.verification.DependencyVerifierBuilder
import org.gradle.api.internal.artifacts.verification.model.ChecksumKind
import org.gradle.integtests.tooling.fixture.TextUtil
import org.gradle.internal.component.external.model.DefaultModuleComponentArtifactIdentifier
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import spock.lang.Specification

class DependencyVerificationsXmlWriterTest extends Specification {
    private final DependencyVerifierBuilder builder = new DependencyVerifierBuilder()
    private String contents

    def "can write an empty file"() {
        when:
        serialize()

        then:
        contents == """<?xml version="1.0" encoding="UTF-8"?>
<verification-metadata>
   <components/>
</verification-metadata>
"""
    }

    // In context of future dependency verification file update, we try
    // to preserve the order of insertion when building the file.
    // There's the exception of checksums themselves, which are ordered
    // based on the ChecksumKind enum.
    // This documents the current behavior, not necessarily what should
    // be done eventually.
    def "order of declaration is preserved (except for checksums)"() {
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
   <components>
      <component group="org" name="foo" version="1.0">
         <artifact name="foo" type="jar" ext="jar">
            <sha1 value="abc"/>
            <sha256 value="bcd"/>
         </artifact>
      </component>
      <component group="org" name="foo" version="1.1">
         <artifact name="foo" type="jar" ext="jar">
            <md5 value="1234"/>
         </artifact>
      </component>
      <component group="org" name="bar" version="1.2">
         <artifact name="bar" type="jar" ext="jar">
            <sha1 value="9876"/>
            <sha512 value="123def"/>
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
   <components>
      <component group="org" name="foo" version="1.0">
         <artifact name="foo" type="jar" ext="jar">
            <sha1 value="abc"/>
         </artifact>
         <artifact name="foo" type="zip" ext="zip">
            <sha256 value="def"/>
         </artifact>
         <artifact name="foo" classifier="classy" type="jar" ext="jar">
            <sha1 value="123"/>
         </artifact>
      </component>
   </components>
</verification-metadata>
"""
    }

    void declareChecksum(String id, String algorithm, String checksum) {
        def (group, name, version) = id.split(":")
        declareChecksumOfArtifact(group, name, version, "jar", "jar", null, algorithm, checksum)
    }

    private declareChecksumOfArtifact(String group, String name, version, String type, String ext, String classifier, String algorithm, String checksum) {
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
            checksum
        )
    }

    private void serialize() {
        def out = new ByteArrayOutputStream()
        DependencyVerificationsXmlWriter.serialize(builder.build(), out)
        contents = TextUtil.normaliseLineSeparators(out.toString("utf-8"))
    }
}
