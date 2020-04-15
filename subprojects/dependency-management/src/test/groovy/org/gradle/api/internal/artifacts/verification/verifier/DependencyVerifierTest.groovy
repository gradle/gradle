/*
 * Copyright 2020 the original author or authors.
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

import org.bouncycastle.openpgp.PGPPublicKey
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.ArtifactVerificationOperation
import org.gradle.api.internal.artifacts.verification.signatures.SignatureVerificationResultBuilder
import org.gradle.api.internal.artifacts.verification.signatures.SignatureVerificationService
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier
import org.gradle.internal.component.external.model.ModuleComponentFileArtifactIdentifier
import org.gradle.internal.hash.ChecksumService
import org.gradle.security.internal.PublicKeyService
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Subject

class DependencyVerifierTest extends Specification {
    @Subject
    DependencyVerifier verifier = new DependencyVerifier([:], new DependencyVerificationConfiguration(true, true, [], [], [] as Set, []))

    ChecksumService checksumService = Mock(ChecksumService)
    PublicKeyService publicKeyService = Mock(PublicKeyService)

    SignatureVerificationService signatureVerificationService = Mock(SignatureVerificationService) {
        getPublicKeyService() >> publicKeyService
    }
    ArtifactVerificationResultBuilder result = Mock(ArtifactVerificationResultBuilder)

    ArtifactVerificationOperation.ArtifactKind kind
    ModuleComponentArtifactIdentifier artifact
    File artifactFile = Mock(File)
    File signatureFile = Mock(File)

    @Issue("https://github.com/gradle/gradle/issues/11999")
    def "handles duplicate keys"() {
        artifact("org", "foo", "1.0")

        when:
        artifactFile.exists() >> true
        signatureFile.exists() >> true
        verify()

        then:
        1 * signatureVerificationService.getPublicKeyService()
        1 * signatureVerificationService.verify(artifactFile, signatureFile, _, _, { SignatureVerificationResultBuilder result ->
            result.verified(Stub(PGPPublicKey), false)
            result.verified(Stub(PGPPublicKey), false)
        })
        1 * result.failWith(_)
        0 * _
    }

    private void artifact(String group, String name, String version) {
        artifact = new ModuleComponentFileArtifactIdentifier(
            DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId(group, name), version),
            "${name}-${version}.jar"
        )
    }

    private verify() {
        verifier.verify(checksumService, signatureVerificationService, kind, artifact, artifactFile, signatureFile, result)
    }
}
