/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.internal.artifacts.verification.verifier


import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.verification.exceptions.ComponentVerificationException
import org.gradle.api.internal.artifacts.verification.exceptions.InvalidGpgKeyIdsException
import org.gradle.internal.component.external.model.DefaultModuleComponentArtifactIdentifier
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import spock.lang.Specification

class DependencyVerifierBuilderTest extends Specification {

    def "ComponentVerificationsBuilder should fail if trusted GPG key is not a fingerprint but a #name"() {
        given:
        def moduleComponentIdentifier = DefaultModuleComponentIdentifier::newId(
            DefaultModuleVersionIdentifier::newId("test.group", "test-module", "0.0.0")
        )
        def componentArtifactIdentified = new DefaultModuleComponentArtifactIdentifier(
            moduleComponentIdentifier, "artifact", "jar", ".jar"
        )

        def dependencyVerifier = new DependencyVerifierBuilder.ComponentVerificationsBuilder(moduleComponentIdentifier);
        keyIds.forEach {
            dependencyVerifier.addTrustedKey(componentArtifactIdentified, it)
        }

        when:
        dependencyVerifier.build()

        then:
        def ex = thrown(ComponentVerificationException)
        println(ex.getMessage())

        where:
        name                  | keyIds
        "short id"            | ["AAAAAAA"]
        "long id"             | ["AAAAAAAAAAAAAA"]
        "mixed short/long id" | ["AAAAAAAA", "AAAAAAAAAAAAAA"]
    }

    def "ArtifactVerificationBuilder should fail if trusted GPG key is not a fingerprint but a #name"() {
        given:
        def verificationBuilder = new DependencyVerifierBuilder.ArtifactVerificationBuilder()
        keyIds.forEach(verificationBuilder::addTrustedKey)

        when:
        verificationBuilder.buildTrustedPgpKeys();

        then:
        def ex = thrown(InvalidGpgKeyIdsException)
        println(ex.getMessage())

        where:
        name                  | keyIds
        "short id"            | ["AAAAAAA"]
        "long id"             | ["AAAAAAAAAAAAAA"]
        "mixed short/long id" | ["AAAAAAAA", "AAAAAAAAAAAAAA"]
    }

    def "ArtifactVerificationBuilder should succeed if trusted GPG key is a fingerprint"() {
        given:
        def verificationBuilder = new DependencyVerifierBuilder.ArtifactVerificationBuilder()
        verificationBuilder.addTrustedKey("d7bf96a169f77b28c934ab1614f53f0824875d73")

        when:
        verificationBuilder.buildTrustedPgpKeys();

        then:
        noExceptionThrown()
    }

}
