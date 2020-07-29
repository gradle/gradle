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
import org.gradle.api.internal.artifacts.verification.model.IgnoredKey;
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier;

import javax.annotation.Nullable;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class DependencyVerificationConfiguration {
    private final boolean verifyMetadata;
    private final boolean verifySignatures;
    private final List<TrustedArtifact> trustedArtifacts;
    private final List<URI> keyServers;
    private final Set<IgnoredKey> ignoredKeys;
    private final List<TrustedKey> trustedKeys;

    public DependencyVerificationConfiguration(boolean verifyMetadata, boolean verifySignatures, List<TrustedArtifact> trustedArtifacts, List<URI> keyServers, Set<IgnoredKey> ignoredKeys, List<TrustedKey> trustedKeys) {
        this.verifyMetadata = verifyMetadata;
        this.verifySignatures = verifySignatures;
        this.trustedArtifacts = ImmutableList.copyOf(trustedArtifacts);
        this.keyServers = keyServers;
        this.ignoredKeys = ignoredKeys;
        this.trustedKeys = trustedKeys;
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

    public abstract static class TrustCoordinates {
        private final String group;
        private final String name;
        private final String version;
        private final String fileName;
        private final boolean regex;

        TrustCoordinates(@Nullable String group, @Nullable String name, @Nullable String version, @Nullable String fileName, boolean regex) {
            this.group = group;
            this.name = name;
            this.version = version;
            this.fileName = fileName;
            this.regex = regex;
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
            return Objects.equals(fileName, that.fileName);
        }

        @Override
        public int hashCode() {
            int result = group != null ? group.hashCode() : 0;
            result = 31 * result + (name != null ? name.hashCode() : 0);
            result = 31 * result + (version != null ? version.hashCode() : 0);
            result = 31 * result + (fileName != null ? fileName.hashCode() : 0);
            result = 31 * result + (regex ? 1 : 0);
            return result;
        }
    }

    public static class TrustedArtifact extends TrustCoordinates {
        TrustedArtifact(@Nullable String group, @Nullable String name, @Nullable String version, @Nullable String fileName, boolean regex) {
            super(group, name, version, fileName, regex);
        }
    }

    public static class TrustedKey extends TrustCoordinates {
        private final String keyId;

        TrustedKey(String keyId, @Nullable String group, @Nullable String name, @Nullable String version, @Nullable String fileName, boolean regex) {
            super(group, name, version, fileName, regex);
            this.keyId = keyId;
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
    }
}
