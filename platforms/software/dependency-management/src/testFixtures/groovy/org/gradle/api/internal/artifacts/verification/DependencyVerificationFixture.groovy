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

import com.google.common.io.Files
import groovy.transform.CompileStatic
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.verification.model.ArtifactVerificationMetadata
import org.gradle.api.internal.artifacts.verification.model.ChecksumKind
import org.gradle.api.internal.artifacts.verification.model.ComponentVerificationMetadata
import org.gradle.api.internal.artifacts.verification.model.IgnoredKey
import org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationsXmlReader
import org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationsXmlWriter
import org.gradle.api.internal.artifacts.verification.verifier.DependencyVerifier
import org.gradle.api.internal.artifacts.verification.verifier.DependencyVerifierBuilder
import org.gradle.internal.component.external.model.DefaultModuleComponentArtifactIdentifier
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.external.model.ModuleComponentFileArtifactIdentifier
import org.gradle.util.internal.TextUtil

@CompileStatic
class DependencyVerificationFixture {
    private final File verificationFile
    private final File dryRunVerificationFile

    private DependencyVerifier verifier

    DependencyVerificationFixture(File verificationFile) {
        this.verificationFile = verificationFile
        this.dryRunVerificationFile = new File(verificationFile.parentFile, "${Files.getNameWithoutExtension(verificationFile.name)}.dryrun.xml")
    }

    void assertMetadataExists() {
        assert verificationFile.exists(): "Verification file ${verificationFile} is missing"
    }

    void assertMetadataIsMissing() {
        assert !verificationFile.exists() : "Expected verification file ${verificationFile} to be absent but it exists"
    }

    void deleteMetadataFile() {
        assertMetadataExists()
        verificationFile.delete()
        verifier = null
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

    void hasIgnoredKeys(Collection<String> ignoredKeys) {
        withVerifier {
            def actualIgnored = configuration.ignoredKeys
            def expected = ignoredKeys as Set
            assert actualIgnored == expected
        }
    }

    void hasTrustedKeys(Collection<String> trustedKeys) {
        withVerifier {
            def actualIgnored = configuration.trustedKeys*.keyId as Set
            def expected = trustedKeys as Set
            assert actualIgnored == expected
        }
    }

    void hasNoModules() {
        hasModules([])
    }

    void assertXmlContents(String expected) {
        def actualContents = TextUtil.normaliseLineSeparators(verificationFile.text)
        // remove namespace declaration for readability of tests
        actualContents = actualContents.replaceAll("<verification-metadata .+>", "<verification-metadata>")
        assert actualContents == expected
    }

    void assertDryRunXmlContents(String expected) {
        def actualContents = TextUtil.normaliseLineSeparators(dryRunVerificationFile.text)
        // remove namespace declaration for readability of tests
        actualContents = actualContents.replaceAll("<verification-metadata .+>", "<verification-metadata>")
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

    void replaceMetadataFile(@DelegatesTo(value = Builder, strategy = Closure.DELEGATE_FIRST) Closure config) {
        assertMetadataExists()
        verificationFile.delete()
        createMetadataFile(config)
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

    void hasNoModule(String id) {
        withVerifier {
            def list = id.split(":")
            def group = list[0]
            def name = list[1]
            def version = list.size() == 3 ? list[2] : "1.0"
            def md = verificationMetadata.find {
                it.componentId.group == group && it.componentId.module == name && it.componentId.version == version
            }
            assert md == null : "Didn't expect module $id to be present but it was"
        }
    }

    static class ComponentVerification {
        private final ComponentVerificationMetadata metadata

        ComponentVerification(ComponentVerificationMetadata md) {
            this.metadata = md
        }

        void artifact(String name, @DelegatesTo(value = ArtifactVerification, strategy = Closure.DELEGATE_FIRST) Closure action) {
            def artifacts = metadata.artifactVerifications.findAll {
                name == it.artifactName
            }
            if (artifacts.size() > 1) {
                throw new AssertionError("Expected only one artifact named ${name} for module ${metadata.componentId} but found ${artifacts}")
            }
            ArtifactVerificationMetadata md = artifacts ? artifacts[0] : null
            assert md: "Artifact file $name not found in verification file for module ${metadata.componentId}. Artifact names: ${metadata.artifactVerifications.collect { it.artifactName } }"
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
            def expectedChecksum = metadata.checksums.find { it.kind == ChecksumKind.valueOf(algorithm) }.value
            assert expectedChecksum == checksum : "On ${metadata.artifactName}, expected a ${algorithm} checksum of ${checksum} but was ${expectedChecksum}"
        }

        void declaresChecksums(List<String> checksums, String algorithm = "sha1") {
            def expectedChecksum = metadata.checksums.find { it.kind == ChecksumKind.valueOf(algorithm) }
            Set<String> allChecksums = [expectedChecksum.value] as Set<String>
            if (expectedChecksum.alternatives) {
                allChecksums.addAll(expectedChecksum.alternatives)
            }

            assert allChecksums == checksums as Set : "On ${metadata.artifactName}, expected ${algorithm} checksums of ${checksums} but was ${allChecksums}"
        }

        void declaresChecksums(Map<String, ?> checksums, boolean strict = true) {
            checksums.forEach { algo, value ->
                if (value instanceof CharSequence) {
                    declaresChecksum(value.toString(), algo)
                } else {
                    declaresChecksums((List) value, algo)
                }
            }
            if (strict) {
                def expectedChecksums = checksums.keySet()
                def actualChecksums = metadata.checksums*.kind*.name() as Set
                assert expectedChecksums == actualChecksums
            }
        }
    }

    static class Builder {
        private final DependencyVerifierBuilder builder = new DependencyVerifierBuilder()

        void disableKeyServers() {
            builder.useKeyServers = false
        }

        void noMetadataVerification() {
            builder.verifyMetadata = false
        }

        void verifySignatures() {
            builder.verifySignatures = true
        }

        void keyServer(String uri) {
            builder.addKeyServer(new URI(uri))
        }

        void keyServer(URI uri) {
            builder.addKeyServer(uri)
        }

        void keyRingFormat(String keyRingFormat) {
            builder.setKeyringFormat(keyRingFormat);
        }

        void trust(String group, String name = null, String version = null, String fileName = null, boolean regex = false, String reason = null) {
            builder.addTrustedArtifact(group, name, version, fileName, regex, reason)
        }

        void addGloballyIgnoredKey(String id, String reason = "for tests") {
            builder.addIgnoredKey(new IgnoredKey(id, reason))
        }

        void addChecksum(String id, String algo, String checksum, String type="jar", String ext="jar", String origin = null, String reason = null) {
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
                    type,
                    ext
                ),
                ChecksumKind.valueOf(algo),
                checksum,
                origin,
                reason
            )
        }

        void addTrustedKey(String id, String key, String type="jar", String ext="jar") {
            def parts = id.split(":")
            def group = parts[0]
            def name = parts[1]
            def version = parts.size() == 3 ? parts[2] : "1.0"
            builder.addTrustedKey(
                new DefaultModuleComponentArtifactIdentifier(
                    DefaultModuleComponentIdentifier.newId(
                        DefaultModuleIdentifier.newId(group, name),
                        version
                    ),
                    name,
                    type,
                    ext
                ),
                key
            )
        }

        void addTrustedKeyByFileName(String id, String fileName, String key) {
            def parts = id.split(":")
            def group = parts[0]
            def name = parts[1]
            def version = parts.size() == 3 ? parts[2] : "1.0"
            builder.addTrustedKey(
                new ModuleComponentFileArtifactIdentifier(
                    DefaultModuleComponentIdentifier.newId(
                        DefaultModuleIdentifier.newId(group, name),
                        version
                    ),
                    fileName
                ),
                key
            )
        }

        void addGloballyTrustedKey(String keyId, String group = null, String name = null, String version = null, String fileName = null, boolean regex = false) {
            builder.addTrustedKey(keyId, group, name, version, fileName, regex)
        }

        void addIgnoredKeyByFileName(String id, String fileName, String key, String reason = "for tests") {
            def parts = id.split(":")
            def group = parts[0]
            def name = parts[1]
            def version = parts.size() == 3 ? parts[2] : "1.0"
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

        DependencyVerifier build() {
            builder.build()
        }
    }
}
