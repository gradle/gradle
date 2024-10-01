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
package org.gradle.api.internal.artifacts.verification.verifier;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.verification.exceptions.ComponentVerificationException;
import org.gradle.api.internal.artifacts.verification.exceptions.DependencyVerificationException;
import org.gradle.api.internal.artifacts.verification.exceptions.InvalidGpgKeyIdsException;
import org.gradle.api.internal.artifacts.verification.model.ArtifactVerificationMetadata;
import org.gradle.api.internal.artifacts.verification.model.Checksum;
import org.gradle.api.internal.artifacts.verification.model.ChecksumKind;
import org.gradle.api.internal.artifacts.verification.model.ComponentVerificationMetadata;
import org.gradle.api.internal.artifacts.verification.model.IgnoredKey;
import org.gradle.api.internal.artifacts.verification.model.ImmutableArtifactVerificationMetadata;
import org.gradle.api.internal.artifacts.verification.model.ImmutableComponentVerificationMetadata;
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier;

import javax.annotation.Nullable;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class DependencyVerifierBuilder {
    private static final Comparator<ModuleComponentIdentifier> MODULE_COMPONENT_IDENTIFIER_COMPARATOR = Comparator.comparing(ModuleComponentIdentifier::getGroup)
        .thenComparing(ModuleComponentIdentifier::getModule)
        .thenComparing(ModuleComponentIdentifier::getVersion);
    private final Map<ModuleComponentIdentifier, ComponentVerificationsBuilder> byComponent = new HashMap<>();
    private final List<DependencyVerificationConfiguration.TrustedArtifact> trustedArtifacts = new ArrayList<>();
    private final Set<DependencyVerificationConfiguration.TrustedKey> trustedKeys = new LinkedHashSet<>();
    private final List<URI> keyServers = new ArrayList<>();
    private final Set<IgnoredKey> ignoredKeys = new LinkedHashSet<>();
    private boolean isVerifyMetadata = true;
    private boolean isVerifySignatures = false;
    private boolean useKeyServers = true;
    private final List<String> topLevelComments = new ArrayList<>();
    private DependencyVerificationConfiguration.KeyringFormat keyringFormat = null;

    public void setKeyringFormat(String newKeyringFormat) {
        this.keyringFormat = parseKeyringFormat(newKeyringFormat);
    }

    private DependencyVerificationConfiguration.KeyringFormat parseKeyringFormat(String keyringFormat) {
        if (keyringFormat == null) {
            return null;
        }
        try {
            return DependencyVerificationConfiguration.KeyringFormat.valueOf(keyringFormat.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new DependencyVerificationException("Invalid keyring format: " + keyringFormat + ". The keyring format should be either 'armored' or 'binary', which determines how keys are stored. Please choose a valid format or leave it unset to generate both.");
        }
    }

    public DependencyVerificationConfiguration.KeyringFormat getKeyringFormat() {
        return keyringFormat;
    }

    public void addTopLevelComment(String comment) {
        topLevelComments.add(comment);
    }

    public void addChecksum(ModuleComponentArtifactIdentifier artifact, ChecksumKind kind, String value, @Nullable String origin, @Nullable String reason) {
        ModuleComponentIdentifier componentIdentifier = artifact.getComponentIdentifier();
        byComponent.computeIfAbsent(componentIdentifier, ComponentVerificationsBuilder::new)
            .addChecksum(artifact, kind, value, origin, reason);
    }

    public void addTrustedKey(ModuleComponentArtifactIdentifier artifact, String key) {
        ModuleComponentIdentifier componentIdentifier = artifact.getComponentIdentifier();
        byComponent.computeIfAbsent(componentIdentifier, ComponentVerificationsBuilder::new)
            .addTrustedKey(artifact, key);
    }

    public void addIgnoredKey(ModuleComponentArtifactIdentifier artifact, IgnoredKey key) {
        ModuleComponentIdentifier componentIdentifier = artifact.getComponentIdentifier();
        byComponent.computeIfAbsent(componentIdentifier, ComponentVerificationsBuilder::new)
            .addIgnoredKey(artifact, key);
    }

    public void setVerifyMetadata(boolean verifyMetadata) {
        isVerifyMetadata = verifyMetadata;
    }

    public boolean isVerifyMetadata() {
        return isVerifyMetadata;
    }

    public boolean isVerifySignatures() {
        return isVerifySignatures;
    }

    public void setVerifySignatures(boolean verifySignatures) {
        isVerifySignatures = verifySignatures;
    }

    public boolean isUseKeyServers() {
        return useKeyServers;
    }

    public void setUseKeyServers(boolean useKeyServers) {
        this.useKeyServers = useKeyServers;
    }

    public List<URI> getKeyServers() {
        return keyServers;
    }

    public Set<DependencyVerificationConfiguration.TrustedKey> getTrustedKeys() {
        return trustedKeys;
    }

    public void addTrustedArtifact(@Nullable String group, @Nullable String name, @Nullable String version, @Nullable String fileName, boolean regex) {
        addTrustedArtifact(group, name, version, fileName, regex, null);
    }

    public void addTrustedArtifact(@Nullable String group, @Nullable String name, @Nullable String version, @Nullable String fileName, boolean regex, @Nullable String reason) {
        validateUserInput(group, name, version, fileName);
        trustedArtifacts.add(new DependencyVerificationConfiguration.TrustedArtifact(group, name, version, fileName, regex, reason));
    }

    public void addIgnoredKey(IgnoredKey keyId) {
        ignoredKeys.add(keyId);
    }

    public void addTrustedKey(String keyId, @Nullable String group, @Nullable String name, @Nullable String version, @Nullable String fileName, boolean regex) {
        validateUserInput(group, name, version, fileName);
        trustedKeys.add(new DependencyVerificationConfiguration.TrustedKey(keyId, group, name, version, fileName, regex));
    }

    private void validateUserInput(@Nullable String group, @Nullable String name, @Nullable String version, @Nullable String fileName) {
        // because this can be called from parsing XML, we need to perform additional verification
        if (group == null && name == null && version == null && fileName == null) {
            throw new DependencyVerificationException("A trusted artifact must have at least one of group, name, version or file name not null");
        }
    }

    public DependencyVerifier build() {
        ImmutableMap.Builder<ModuleComponentIdentifier, ComponentVerificationMetadata> builder = ImmutableMap.builderWithExpectedSize(byComponent.size());
        byComponent.entrySet().stream()
            .sorted(Map.Entry.comparingByKey(MODULE_COMPONENT_IDENTIFIER_COMPARATOR))
            .forEachOrdered(entry -> builder.put(entry.getKey(), entry.getValue().build()));
        return new DependencyVerifier(builder.build(), new DependencyVerificationConfiguration(isVerifyMetadata, isVerifySignatures, trustedArtifacts, useKeyServers, ImmutableList.copyOf(keyServers), ImmutableSet.copyOf(ignoredKeys), ImmutableList.copyOf(trustedKeys), keyringFormat), topLevelComments);
    }

    public List<DependencyVerificationConfiguration.TrustedArtifact> getTrustedArtifacts() {
        return trustedArtifacts;
    }

    public void addKeyServer(URI uri) {
        keyServers.add(uri);
    }

    protected static class ComponentVerificationsBuilder {
        private final ModuleComponentIdentifier component;
        private final Map<String, ArtifactVerificationBuilder> byArtifact = new HashMap<>();

        protected ComponentVerificationsBuilder(ModuleComponentIdentifier component) {
            this.component = component;
        }

        void addChecksum(ModuleComponentArtifactIdentifier artifact, ChecksumKind kind, String value, @Nullable String origin, @Nullable String reason) {
            byArtifact.computeIfAbsent(artifact.getFileName(), id -> new ArtifactVerificationBuilder()).addChecksum(kind, value, origin, reason);
        }

        void addTrustedKey(ModuleComponentArtifactIdentifier artifact, String key) {
            byArtifact.computeIfAbsent(artifact.getFileName(), id -> new ArtifactVerificationBuilder()).addTrustedKey(key);
        }

        void addIgnoredKey(ModuleComponentArtifactIdentifier artifact, IgnoredKey key) {
            byArtifact.computeIfAbsent(artifact.getFileName(), id -> new ArtifactVerificationBuilder()).addIgnoredKey(key);
        }

        private static ArtifactVerificationMetadata toArtifactVerification(Map.Entry<String, ArtifactVerificationBuilder> entry) throws InvalidGpgKeyIdsException {
            String key = entry.getKey();
            ArtifactVerificationBuilder value = entry.getValue();
            return new ImmutableArtifactVerificationMetadata(
                key,
                value.buildChecksums(),
                value.buildTrustedPgpKeys(),
                value.buildIgnoredPgpKeys());
        }

        ComponentVerificationMetadata build() {
            try {
                return new ImmutableComponentVerificationMetadata(component,
                    byArtifact.entrySet()
                        .stream()
                        .map(ComponentVerificationsBuilder::toArtifactVerification)
                        .sorted(Comparator.comparing(ArtifactVerificationMetadata::getArtifactName))
                        .collect(Collectors.toList())
                );
            } catch (InvalidGpgKeyIdsException ex) {
                throw new ComponentVerificationException(component, ex::formatMessage);
            }
        }
    }

    protected static class ArtifactVerificationBuilder {
        private final Map<ChecksumKind, ChecksumBuilder> builder = Maps.newEnumMap(ChecksumKind.class);
        private final Set<String> pgpKeys = new LinkedHashSet<>();
        private final Set<IgnoredKey> ignoredPgpKeys = new LinkedHashSet<>();

        void addChecksum(ChecksumKind kind, String value, @Nullable String origin, @Nullable String reason) {
            ChecksumBuilder builder = this.builder.computeIfAbsent(kind, ChecksumBuilder::new);
            builder.addChecksum(value);
            if (origin != null) {
                builder.withOrigin(origin);
            }
            if (reason != null) {
                builder.withReason(reason);
            }
        }

        List<Checksum> buildChecksums() {
            return builder.values()
                .stream()
                .map(ChecksumBuilder::build)
                .sorted(Comparator.comparing(Checksum::getKind))
                .collect(Collectors.toList());
        }

        public void addTrustedKey(String key) {
            pgpKeys.add(key.toUpperCase(Locale.ROOT));
        }

        public void addIgnoredKey(IgnoredKey key) {
            ignoredPgpKeys.add(key);
        }

        /**
         * Builds the list of trusted GPG keys.
         * <p>
         * This method will verify if all the trusted keys are in 160-bit fingerprint format.
         * We do not accept either short or long formats, as they can be vulnerable to collision attacks.
         *
         * <p>
         * Note: the fingerprints' formatting is not verified (i.e. if it's true base32 or not) at this stage.
         * It will happen when these fingerprints will be converted to {@link org.gradle.security.internal.Fingerprint}.
         *
         * @return a set of trusted GPG keys
         * @throws InvalidGpgKeyIdsException if keys not fitting the requirements were found
         */
        public Set<String> buildTrustedPgpKeys() throws InvalidGpgKeyIdsException {
            final List<String> wrongPgpKeys = pgpKeys
                .stream()
                // The key is 160 bits long, encoded in base32 (case-insensitive characters).
                //
                // Base32 gives us 4 bits per character, so the whole fingerprint will be:
                // (160 bits) / (4 bits / character) = 40 characters
                //
                // By getting ASCII bytes (aka. strictly 1 byte per character, no variable-length magic)
                // we can safely check if the fingerprint is of the correct length.
                .filter(key -> key.getBytes(StandardCharsets.US_ASCII).length < 40)
                .collect(Collectors.toList());

            if (wrongPgpKeys.isEmpty()) {
                return pgpKeys;
            } else {
                throw new InvalidGpgKeyIdsException(wrongPgpKeys);
            }
        }

        public Set<IgnoredKey> buildIgnoredPgpKeys() {
            return ignoredPgpKeys;
        }
    }

    private static class ChecksumBuilder {
        private final ChecksumKind kind;
        private String value;
        private String origin;
        private String reason;
        private Set<String> alternatives;

        private ChecksumBuilder(ChecksumKind kind) {
            this.kind = kind;
        }

        /**
         * Sets the origin, if not set already. This is
         * mostly used for automatic generation of checksums
         */
        void withOrigin(String origin) {
            if (this.origin == null) {
                this.origin = origin;
            }
        }

        /**
         * Sets the reason, if not set already.
         */
        void withReason(String reason) {
            if (this.reason == null) {
                this.reason = reason;
            }
        }

        void addChecksum(String checksum) {
            if (value == null) {
                value = checksum;
            } else if (!value.equals(checksum)) {
                if (alternatives == null) {
                    alternatives = new LinkedHashSet<>();
                }
                alternatives.add(checksum);
            }
        }

        Checksum build() {
            return new Checksum(
                kind,
                value,
                alternatives,
                origin,
                reason
            );
        }
    }
}
