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
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.verification.exceptions.InvalidGpgKeyIdsException;
import org.gradle.api.internal.artifacts.verification.model.IgnoredKey;
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier;

import javax.annotation.Nullable;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class DependencyVerificationConfiguration {
    private final boolean verifyMetadata;
    private final boolean verifySignatures;
    private final List<TrustedArtifact> trustedArtifacts;
    private final boolean useKeyServers;
    private final List<URI> keyServers;
    private final Set<IgnoredKey> ignoredKeys;
    private final List<TrustedKey> trustedKeys;
    private final String keyRingFormat;

    public DependencyVerificationConfiguration(boolean verifyMetadata,
                                               boolean verifySignatures,
                                               List<TrustedArtifact> trustedArtifacts,
                                               boolean useKeyServers,
                                               List<URI> keyServers,
                                               Set<IgnoredKey> ignoredKeys,
                                               List<TrustedKey> trustedKeys,
                                               String keyRingFormat) {
        this.verifyMetadata = verifyMetadata;
        this.verifySignatures = verifySignatures;
        this.trustedArtifacts = ImmutableList.copyOf(trustedArtifacts);
        this.useKeyServers = useKeyServers;
        this.keyServers = keyServers;
        this.ignoredKeys = ignoredKeys;
        this.trustedKeys = trustedKeys;
        this.keyRingFormat = keyRingFormat;
    }

    public String getKeyRingFormat() {
        return keyRingFormat;
    }

    public boolean isVerifySignatures() {
        return verifySignatures;
    }

    public boolean isVerifyMetadata() {
        return verifyMetadata;
    }

    public List<TrustedArtifact> getTrustedArtifacts() {
        return trustedArtifacts;
    }

    public List<URI> getKeyServers() {
        return keyServers;
    }

    public Set<IgnoredKey> getIgnoredKeys() {
        return ignoredKeys;
    }

    public List<TrustedKey> getTrustedKeys() {
        return trustedKeys;
    }

    public boolean isUseKeyServers() {
        return useKeyServers;
    }

    public abstract static class TrustCoordinates {
        private final String group;
        private final String name;
        private final String version;
        private final String fileName;
        private final boolean regex;
        private final String reason;

        TrustCoordinates(@Nullable String group, @Nullable String name, @Nullable String version, @Nullable String fileName, boolean regex, @Nullable String reason) {
            this.group = group;
            this.name = name;
            this.version = version;
            this.fileName = fileName;
            this.regex = regex;
            this.reason = reason;
        }

        public String getGroup() {
            return group;
        }

        public String getName() {
            return name;
        }

        public String getVersion() {
            return version;
        }

        public String getFileName() {
            return fileName;
        }

        public boolean isRegex() {
            return regex;
        }

        public String getReason() {
            return reason;
        }

        public boolean matches(ModuleComponentArtifactIdentifier id) {
            ModuleComponentIdentifier moduleComponentIdentifier = id.getComponentIdentifier();
            return matches(group, moduleComponentIdentifier.getGroup())
                && matches(name, moduleComponentIdentifier.getModule())
                && matches(version, moduleComponentIdentifier.getVersion())
                && matches(fileName, id.getFileName());
        }

        private boolean matches(@Nullable String value, String expr) {
            if (value == null) {
                return true;
            }
            if (!regex) {
                return expr.equals(value);
            }
            return expr.matches(value);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            TrustCoordinates that = (TrustCoordinates) o;

            if (regex != that.regex) {
                return false;
            }
            if (!Objects.equals(group, that.group)) {
                return false;
            }
            if (!Objects.equals(name, that.name)) {
                return false;
            }
            if (!Objects.equals(version, that.version)) {
                return false;
            }
            if (!Objects.equals(fileName, that.fileName)) {
                return false;
            }
            return Objects.equals(reason, that.reason);
        }

        @Override
        public int hashCode() {
            int result = group != null ? group.hashCode() : 0;
            result = 31 * result + (name != null ? name.hashCode() : 0);
            result = 31 * result + (version != null ? version.hashCode() : 0);
            result = 31 * result + (fileName != null ? fileName.hashCode() : 0);
            result = 31 * result + (regex ? 1 : 0);
            result = 31 * result + (reason != null ? reason.hashCode() : 0);
            return result;
        }

        public int internalCompareTo(TrustCoordinates other) {
            int regexComparison = Boolean.compare(isRegex(), other.isRegex());
            if (regexComparison != 0) {
                return regexComparison;
            }
            int groupComparison = compareNullableStrings(getGroup(), other.getGroup());
            if (groupComparison != 0) {
                return groupComparison;
            }
            int nameComparison = compareNullableStrings(getName(), other.getName());
            if (nameComparison != 0) {
                return nameComparison;
            }
            int versionComparison = compareNullableStrings(getVersion(), other.getVersion());
            if (versionComparison != 0) {
                return versionComparison;
            }
            int fileNameComparison = compareNullableStrings(getFileName(), other.getFileName());
            if (fileNameComparison != 0) {
                return fileNameComparison;
            }
            return compareNullableStrings(getReason(), other.getReason());
        }
    }

    public static class TrustedArtifact extends TrustCoordinates implements Comparable<TrustedArtifact> {
        TrustedArtifact(@Nullable String group, @Nullable String name, @Nullable String version, @Nullable String fileName, boolean regex, @Nullable String reason) {
            super(group, name, version, fileName, regex, reason);
        }

        @Override
        public int compareTo(DependencyVerificationConfiguration.TrustedArtifact other) {
            return internalCompareTo(other);
        }
    }

    public static class TrustedKey extends TrustCoordinates implements Comparable<TrustedKey> {
        private final String keyId;

        TrustedKey(String keyId, @Nullable String group, @Nullable String name, @Nullable String version, @Nullable String fileName, boolean regex) {
            super(group, name, version, fileName, regex, null);

            // The key is 160 bits long, encoded in base32 (case-insensitive characters).
            //
            // Base32 gives us 4 bits per character, so the whole fingerprint will be:
            // (160 bits) / (4 bits / character) = 40 characters
            //
            // By getting ASCII bytes (aka. strictly 1 byte per character, no variable-length magic)
            // we can safely check if the fingerprint is of the correct length.
            if (keyId.getBytes(StandardCharsets.US_ASCII).length < 40) {
                throw new InvalidGpgKeyIdsException(Collections.singletonList(keyId));
            } else {
                this.keyId = keyId;
            }
        }

        public String getKeyId() {
            return keyId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            if (!super.equals(o)) {
                return false;
            }

            TrustedKey that = (TrustedKey) o;

            return keyId.equals(that.keyId);
        }

        @Override
        public int hashCode() {
            int result = super.hashCode();
            result = 31 * result + keyId.hashCode();
            return result;
        }

        @Override
        public int compareTo(DependencyVerificationConfiguration.TrustedKey other) {
            int keyIdComparison = getKeyId().compareTo(other.getKeyId());
            if (keyIdComparison != 0) {
                return keyIdComparison;
            }
            return internalCompareTo(other);
        }

    }

    private static int compareNullableStrings(String first, String second) {
        if (first == null) {
            if (second == null) {
                return 0;
            } else {
                return -1;
            }
        } else if (second == null) {
            return 1;
        }
        return first.compareTo(second);
    }
}
