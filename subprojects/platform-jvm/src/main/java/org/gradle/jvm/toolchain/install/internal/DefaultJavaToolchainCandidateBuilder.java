/*
 * Copyright 2021 the original author or authors.
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
package org.gradle.jvm.toolchain.install.internal;

import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainCandidate;
import org.gradle.jvm.toolchain.JvmImplementation;

import javax.annotation.Nullable;
import java.util.Optional;

public class DefaultJavaToolchainCandidateBuilder implements JavaToolchainCandidate.Builder {
    private final String defaultOs;
    private final String defaultArch;

    public DefaultJavaToolchainCandidateBuilder(String defaultOs, String defaultArch) {
        this.defaultOs = defaultOs;
        this.defaultArch = defaultArch;
    }

    @Override
    public JavaToolchainCandidate.BuilderWithVendor withVendor(String vendor) {
        return new DefaultJavaToolchainCandidateBuilderWithVendor(vendor, defaultArch, defaultOs);
    }

    private static class DefaultJavaToolchainCandidateBuilderWithVendor implements JavaToolchainCandidate.BuilderWithVendor {
        private final String vendor;
        private final String os;
        private final String arch;

        private DefaultJavaToolchainCandidateBuilderWithVendor(String vendor, String defaultArch, String defaultOs) {
            this.vendor = vendor;
            this.os = defaultOs;
            this.arch = defaultArch;
        }

        @Override
        public JavaToolchainCandidate.BuilderWithLanguageVersion withLanguageVersion(int version) {
            return new DefaultJavaToolchainCandidateBuilderWithLanguageVersion(vendor, JavaLanguageVersion.of(version), null, arch, os);
        }

        @Override
        public JavaToolchainCandidate.BuilderWithLanguageVersion withLanguageVersion(String version) {
            return new DefaultJavaToolchainCandidateBuilderWithLanguageVersion(vendor, JavaLanguageVersion.of(version), null, arch, os);
        }

        @Override
        public JavaToolchainCandidate.BuilderWithLanguageVersion withLanguageVersion(JavaLanguageVersion version) {
            return new DefaultJavaToolchainCandidateBuilderWithLanguageVersion(vendor, version, null, arch, os);
        }
    }

    private static class DefaultJavaToolchainCandidateBuilderWithLanguageVersion implements JavaToolchainCandidate.BuilderWithLanguageVersion {
        private final String vendor;
        private final JavaLanguageVersion javaLanguageVersion;
        private final JvmImplementation implementation;
        private final String arch;
        private final String os;

        private DefaultJavaToolchainCandidateBuilderWithLanguageVersion(
            String vendor,
            JavaLanguageVersion javaLanguageVersion,
            @Nullable JvmImplementation implementation,
            String arch,
            String os
        ) {
            this.vendor = vendor;
            this.javaLanguageVersion = javaLanguageVersion;
            this.implementation = implementation;
            this.arch = arch;
            this.os = os;
        }

        @Override
        public JavaToolchainCandidate.BuilderWithLanguageVersion withImplementation(JvmImplementation implementation) {
            return new DefaultJavaToolchainCandidateBuilderWithLanguageVersion(vendor, javaLanguageVersion, implementation, arch, os);
        }

        @Override
        public JavaToolchainCandidate.BuilderWithLanguageVersion withArch(String arch) {
            return new DefaultJavaToolchainCandidateBuilderWithLanguageVersion(vendor, javaLanguageVersion, implementation, arch, os);
        }

        @Override
        public JavaToolchainCandidate.BuilderWithLanguageVersion withOperatingSystem(String os) {
            return new DefaultJavaToolchainCandidateBuilderWithLanguageVersion(vendor, javaLanguageVersion, implementation, arch, os);
        }

        @Override
        public JavaToolchainCandidate build() {
            return new DefaultJavaToolchainCandidate(vendor, javaLanguageVersion, implementation, arch, os);
        }
    }

    private static class DefaultJavaToolchainCandidate implements JavaToolchainCandidate {
        private final String vendor;
        private final JavaLanguageVersion languageVersion;
        private final JvmImplementation implementation;
        private final String arch;
        private final String os;

        private DefaultJavaToolchainCandidate(
            String vendor,
            JavaLanguageVersion languageVersion,
            @Nullable JvmImplementation implementation,
            String arch,
            String os
        ) {
            this.vendor = vendor;
            this.languageVersion = languageVersion;
            this.implementation = implementation;
            this.arch = arch;
            this.os = os;
        }

        @Override
        public JavaLanguageVersion getLanguageVersion() {
            return languageVersion;
        }

        @Override
        public String getVendor() {
            return vendor;
        }

        @Override
        public Optional<JvmImplementation> getImplementation() {
            return Optional.ofNullable(implementation);
        }

        @Override
        public String getArch() {
            return arch;
        }

        @Override
        public String getOperatingSystem() {
            return os;
        }

    }
}
