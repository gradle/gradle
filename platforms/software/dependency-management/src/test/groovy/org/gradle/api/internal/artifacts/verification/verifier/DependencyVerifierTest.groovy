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
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.ArtifactVerificationOperation
import org.gradle.api.internal.artifacts.verification.model.Checksum
import org.gradle.api.internal.artifacts.verification.model.ChecksumKind
import org.gradle.api.internal.artifacts.verification.model.ImmutableArtifactVerificationMetadata
import org.gradle.api.internal.artifacts.verification.model.ImmutableComponentVerificationMetadata
import org.gradle.api.internal.artifacts.verification.signatures.SignatureVerificationResultBuilder
import org.gradle.api.internal.artifacts.verification.signatures.SignatureVerificationService
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier
import org.gradle.internal.component.external.model.ModuleComponentFileArtifactIdentifier
import org.gradle.internal.hash.ChecksumService
import org.gradle.internal.hash.HashCode
import org.gradle.security.internal.PublicKeyService
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Subject

class DependencyVerifierTest extends Specification {
    @Subject
    DependencyVerifier verifier = new DependencyVerifier([:], new DependencyVerificationConfiguration(true, true, [], true, [], [] as Set, [], null), [])

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

    @Issue("https://github.com/gradle/gradle/issues/19089")
    def "should not fail when custom implementation of ModuleComponentIdentifier is verified"() {
        def defaultModuleComponentIdentifier = DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("org", "foo"), "1.0")
        def checksum = new Checksum(ChecksumKind.md5, "my-checksum", [] as Set<String>, "", "")
        def verificationMetadata = new ImmutableComponentVerificationMetadata(defaultModuleComponentIdentifier, [new ImmutableArtifactVerificationMetadata("foo-1.0.jar", [checksum], [] as Set, [] as Set)])
        verifier = new DependencyVerifier([(defaultModuleComponentIdentifier): verificationMetadata], new DependencyVerificationConfiguration(true, false, [], true, [], [] as Set, [], null), [])
        def hashCode = Mock(HashCode) { toString() >> "my-checksum" }

        when:
        artifactFile.exists() >> true
        def myModuleComponentIdentifier = new MyModuleComponentIdentifier("org", "foo", "1.0")
        def artifact = new ModuleComponentFileArtifactIdentifier(myModuleComponentIdentifier, "foo-1.0.jar")
        verifier.verify(checksumService, signatureVerificationService, kind, artifact, artifactFile, null, result)

        then:
        1 * checksumService.md5(artifactFile) >> hashCode
        1 * signatureVerificationService.getPublicKeyService()
        0 * result.failWith(_)
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

    class MyModuleComponentIdentifier implements ModuleComponentIdentifier {
        private String group
        private String module
        private String version

        MyModuleComponentIdentifier(String group, String module, String version) {
            this.group = group
            this.module = module
            this.version = version
        }

        @Override
        String getDisplayName() {
            return "$group:$module:$version"
        }

        @Override
        String getGroup() {
            return group
        }

        @Override
        String getModule() {
            return module
        }

        @Override
        String getVersion() {
            return version
        }

        @Override
        ModuleIdentifier getModuleIdentifier() {
            return new ModuleIdentifier() {
                @Override
                String getGroup() {
                    return group
                }

                @Override
                String getName() {
                    return module
                }
            }
        }
    }
}
