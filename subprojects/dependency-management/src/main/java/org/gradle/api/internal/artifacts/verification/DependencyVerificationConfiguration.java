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
package org.gradle.api.internal.artifacts.verification;

import com.google.common.collect.ImmutableList;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier;

import javax.annotation.Nullable;
import java.util.List;

public class DependencyVerificationConfiguration {
    private final boolean verifyMetadata;
    private final List<TrustedArtifact> trustedArtifacts;

    public DependencyVerificationConfiguration(boolean verifyMetadata, List<TrustedArtifact> trustedArtifacts) {
        this.verifyMetadata = verifyMetadata;
        this.trustedArtifacts = ImmutableList.copyOf(trustedArtifacts);
    }

    public boolean isVerifyMetadata() {
        return verifyMetadata;
    }

    public List<TrustedArtifact> getTrustedArtifacts() {
        return trustedArtifacts;
    }

    public static class TrustedArtifact {
        private final String group;
        private final String name;
        private final String version;
        private final String fileName;
        private final boolean regex;

        TrustedArtifact(@Nullable String group, @Nullable String name, @Nullable String version, @Nullable String fileName, boolean regex) {
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

        public boolean isTrusted(ModuleComponentArtifactIdentifier id) {
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
    }
}
