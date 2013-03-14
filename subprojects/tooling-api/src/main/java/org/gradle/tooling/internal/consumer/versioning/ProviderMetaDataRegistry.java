/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.tooling.internal.consumer.versioning;

import org.gradle.tooling.internal.protocol.*;
import org.gradle.tooling.internal.protocol.eclipse.EclipseProjectVersion3;
import org.gradle.tooling.internal.protocol.eclipse.HierarchicalEclipseProjectVersion1;
import org.gradle.util.GradleVersion;

public class ProviderMetaDataRegistry {
    private static final GradleVersion M3 = GradleVersion.version("1.0-milestone-3");
    private static final GradleVersion M5 = GradleVersion.version("1.0-milestone-5");
    private static final GradleVersion M8 = GradleVersion.version("1.0-milestone-8");
    private static final GradleVersion V1_2_RC_1 = GradleVersion.version("1.2-rc-1");
    private static final GradleVersion V1_6_RC_1 = GradleVersion.version("1.6-rc-1");

    public VersionDetails getVersionDetails(String providerVersion) {
        GradleVersion version = GradleVersion.version(providerVersion);
        if (version.compareTo(M3) < 0) {
            throw new IllegalArgumentException("Unexpected provider version requested.");
        }
        if (version.compareTo(M5) < 0) {
            return new M3VersionDetails(providerVersion);
        }
        if (version.compareTo(M8) < 0) {
            return new M5VersionDetails(providerVersion);
        }
        if (version.compareTo(V1_2_RC_1) < 0) {
            return new M8VersionDetails(providerVersion);
        }
        if (version.compareTo(V1_6_RC_1) < 0) {
            return new R12VersionDetails(providerVersion);
        }
        return new R16VersionDetails(providerVersion);
    }

    private static class M3VersionDetails extends VersionDetails {
        public M3VersionDetails(String providerVersion) {
            super(providerVersion);
        }

        @Override
        public boolean isModelSupported(Class<?> protocolModelType) {
            if (protocolModelType.equals(HierarchicalEclipseProjectVersion1.class)) {
                return true;
            }
            if (protocolModelType.equals(EclipseProjectVersion3.class)) {
                return true;
            }
            return super.isModelSupported(protocolModelType);
        }
    }

    private static class M5VersionDetails extends M3VersionDetails {
        public M5VersionDetails(String providerVersion) {
            super(providerVersion);
        }

        @Override
        public boolean isModelSupported(Class<?> protocolModelType) {
            if (protocolModelType.equals(InternalIdeaProject.class)) {
                return true;
            }
            if (protocolModelType.equals(InternalBasicIdeaProject.class)) {
                return true;
            }
            if (protocolModelType.equals(InternalGradleProject.class)) {
                return true;
            }
            return super.isModelSupported(protocolModelType);
        }
    }

    private static class M8VersionDetails extends M5VersionDetails {
        public M8VersionDetails(String providerVersion) {
            super(providerVersion);
        }

        @Override
        public boolean isModelSupported(Class<?> protocolModelType) {
            if (protocolModelType.equals(InternalBuildEnvironment.class)) {
                return true;
            }
            return super.isModelSupported(protocolModelType);
        }
    }

    private static class R12VersionDetails extends M8VersionDetails {
        public R12VersionDetails(String providerVersion) {
            super(providerVersion);
        }

        @Override
        public boolean isModelSupported(Class<?> protocolModelType) {
            if (protocolModelType.equals(InternalProjectOutcomes.class)) {
                return true;
            }
            if (protocolModelType.equals(Void.class)) {
                return true;
            }
            return super.isModelSupported(protocolModelType);
        }
    }

    private static class R16VersionDetails extends VersionDetails {
        public R16VersionDetails(String providerVersion) {
            super(providerVersion);
        }

        @Override
        public boolean isModelSupported(Class<?> protocolModelType) {
            return true;
        }
    }
}
