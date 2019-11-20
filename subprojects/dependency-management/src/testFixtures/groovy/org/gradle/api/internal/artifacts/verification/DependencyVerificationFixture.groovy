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

package org.gradle.api.internal.artifacts.verification

import groovy.transform.CompileStatic
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.verification.model.ArtifactVerificationMetadata
import org.gradle.api.internal.artifacts.verification.model.ChecksumKind
import org.gradle.api.internal.artifacts.verification.model.ComponentVerificationMetadata
import org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationsXmlReader
import org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationsXmlWriter
import org.gradle.internal.component.external.model.DefaultModuleComponentArtifactIdentifier
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.util.TextUtil

@CompileStatic
class DependencyVerificationFixture {
    private final File verificationFile

    private DependencyVerifier verifier

    DependencyVerificationFixture(File verificationFile) {
        this.verificationFile = verificationFile
    }

    void assertMetadataExists() {
        assert verificationFile.exists(): "Verification file ${verificationFile} is missing"
    }

    void assertMetadataIsMissing() {
        assert !verificationFile.exists() : "Expected verification file ${verificationFile} to be absent but it exists"
    }

    void hasModules(List<String> modules, boolean strictly = true) {
        withVerifier {
            def seenModules = verificationMetadata.collect {
                "${it.componentId.group}:${it.componentId.module}" as String
            } as Set<String>
            if (strictly) {
                def expectedModules = modules as Set<String>
                assert expectedModules == seenModules
            } else {
                assert seenModules.containsAll(modules)
            }
        }
    }

    void hasNoModules() {
        hasModules([])
    }

    void assertXmlContents(String expected) {
        def actualContents = TextUtil.normaliseLineSeparators(verificationFile.text)
        assert actualContents == expected
    }

    private void withVerifier(@DelegatesTo(value = DependencyVerifier, strategy = Closure.DELEGATE_FIRST) Closure action) {
        if (verifier == null) {
            assertMetadataExists()
            verifier = DependencyVerificationsXmlReader.readFromXml(verificationFile.newInputStream())
        }
        action.delegate = verifier
        action.resolveStrategy = Closure.DELEGATE_FIRST
        action()
    }

    void createMetadataFile(@DelegatesTo(value = Builder, strategy = Closure.DELEGATE_FIRST) Closure config) {
        assertMetadataIsMissing()
        verificationFile.parentFile.mkdirs()
        def builder = new Builder()
        config.resolveStrategy = Closure.DELEGATE_FIRST
        config.delegate = builder
        config()
        DependencyVerificationsXmlWriter.serialize(builder.build(), new FileOutputStream(verificationFile))
    }

    void module(String id, @DelegatesTo(value = ComponentVerification, strategy = Closure.DELEGATE_FIRST) Closure action) {
        withVerifier {
            def list = id.split(":")
            def group = list[0]
            def name = list[1]
            def version = list.size() == 3 ? list[2] : "1.0"
            def md = verificationMetadata.find {
                it.componentId.group == group && it.componentId.module == name && it.componentId.version == version
            }
            assert md: "Dependency verification file contains no information about module ${group:name:version}"
            action.delegate = new ComponentVerification(md)
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action()
        }
    }

    static class ComponentVerification {
        private final ComponentVerificationMetadata metadata

        ComponentVerification(ComponentVerificationMetadata md) {
            this.metadata = md
        }

        void artifact(String name, String type="jar", String ext="jar", String classy = null, @DelegatesTo(value = ArtifactVerification, strategy = Closure.DELEGATE_FIRST) Closure action) {
            def artifacts = metadata.artifactVerifications.findAll {
                def ivy = it.artifact.name
                ivy.name == name && ivy.type == type && ivy.extension == ext && ivy.classifier == classy
            }
            if (artifacts.size() > 1) {
                throw new AssertionError("Expected only one artifact named ${name} for module ${metadata.componentId} but found ${artifacts}")
            }
            ArtifactVerificationMetadata md = artifacts ? artifacts[0] : null
            assert md: "Artifact file $name not found in verification file for module ${metadata.componentId}. Artifact names: ${metadata.artifactVerifications.collect { it.artifact.name.name } }"
            action.delegate = new ArtifactVerification(md)
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action()
        }
    }

    static class ArtifactVerification {
        private final ArtifactVerificationMetadata metadata

        ArtifactVerification(ArtifactVerificationMetadata md) {
            this.metadata = md
        }

        void declaresChecksum(String checksum, String algorithm = "sha1") {
            def expectedChecksum = metadata.checksums.get(ChecksumKind.valueOf(algorithm))
            assert expectedChecksum == checksum
        }

        void declaresChecksums(Map<String, String> checksums, boolean strict = true) {
            checksums.forEach { algo, value ->
                declaresChecksum(value, algo)
            }
            if (strict) {
                def expectedChecksums = checksums.keySet()
                def actualChecksums = metadata.checksums.keySet()*.name() as Set
                assert expectedChecksums == actualChecksums
            }
        }
    }

    static class Builder {
        private final DependencyVerifierBuilder builder = new DependencyVerifierBuilder()

        void addChecksum(String id, String algo, String checksum) {
            def parts = id.split(":")
            def group = parts[0]
            def name = parts[1]
            def version = parts.size() == 3 ? parts[2] : "1.0"
            builder.addChecksum(
                new DefaultModuleComponentArtifactIdentifier(
                    DefaultModuleComponentIdentifier.newId(
                        DefaultModuleIdentifier.newId(group, name),
                        version
                    ),
                    name,
                    "jar",
                    "jar"
                ),
                ChecksumKind.valueOf(algo),
                checksum
            )
        }

        DependencyVerifier build() {
            builder.build()
        }
    }
}
