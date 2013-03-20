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

import org.gradle.api.Nullable;
import org.gradle.tooling.internal.protocol.InternalProtocolInterface;
import org.gradle.tooling.internal.protocol.ModelIdentifier;
import org.gradle.tooling.internal.protocol.ProjectVersion3;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.gradle.tooling.model.eclipse.EclipseProject;
import org.gradle.tooling.model.eclipse.HierarchicalEclipseProject;
import org.gradle.tooling.model.idea.BasicIdeaProject;
import org.gradle.tooling.model.idea.IdeaProject;
import org.gradle.tooling.model.internal.outcomes.ProjectOutcomes;
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

        @Nullable
        public Class<?> mapModelTypeToProtocolType(Class<?> modelType) {
            if (ProjectVersion3.class.isAssignableFrom(modelType) || InternalProtocolInterface.class.isAssignableFrom(modelType)) {
                return modelType;
            }
            return new ModelMapping().getProtocolType(modelType);
        }

        @Override
        public ModelIdentifier mapModelTypeToModelIdentifier(Class<?> modelType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isModelSupported(Class<?> modelType) {
            if (modelType.equals(HierarchicalEclipseProject.class)) {
                return true;
            }
            if (modelType.equals(EclipseProject.class)) {
                return true;
            }
            if (modelType.equals(Void.class)) {
                return true;
            }
            return super.isModelSupported(modelType);
        }
    }

    private static class M5VersionDetails extends M3VersionDetails {
        public M5VersionDetails(String providerVersion) {
            super(providerVersion);
        }

        @Override
        public boolean isModelSupported(Class<?> modelType) {
            if (modelType.equals(IdeaProject.class)) {
                return true;
            }
            if (modelType.equals(BasicIdeaProject.class)) {
                return true;
            }
            if (modelType.equals(GradleProject.class)) {
                return true;
            }
            return super.isModelSupported(modelType);
        }

        @Override
        public boolean supportsGradleProjectModel() {
            return true;
        }
    }

    private static class M8VersionDetails extends M5VersionDetails {
        public M8VersionDetails(String providerVersion) {
            super(providerVersion);
        }

        @Override
        public boolean isModelSupported(Class<?> modelType) {
            if (modelType.equals(BuildEnvironment.class)) {
                return true;
            }
            return super.isModelSupported(modelType);
        }

        @Override
        public boolean supportsConfiguringJavaHome() {
            return true;
        }

        @Override
        public boolean supportsConfiguringJvmArguments() {
            return true;
        }

        @Override
        public boolean supportsConfiguringStandardInput() {
            return true;
        }
    }

    private static class R12VersionDetails extends M8VersionDetails {
        public R12VersionDetails(String providerVersion) {
            super(providerVersion);
        }

        @Override
        public boolean isModelSupported(Class<?> modelType) {
            if (modelType.equals(ProjectOutcomes.class)) {
                return true;
            }
            if (modelType.equals(Void.class)) {
                return true;
            }
            return super.isModelSupported(modelType);
        }

        @Override
        public boolean supportsRunningTasksWhenBuildingModel() {
            return true;
        }
    }

    private static class R16VersionDetails extends R12VersionDetails {
        public R16VersionDetails(String providerVersion) {
            super(providerVersion);
        }

        @Override
        public boolean isModelSupported(Class<?> modelType) {
            return true;
        }

        @Override
        public ModelIdentifier mapModelTypeToModelIdentifier(final Class<?> modelType) {
            return new ModelIdentifier() {
                public String getName() {
                    if (modelType.equals(Void.class)) {
                        return ModelIdentifier.NULL_MODEL;
                    }
                    String modelName = new ModelMapping().getModelName(modelType);
                    if (modelName != null) {
                        return modelName;
                    }
                    return modelType.getName();
                }

                public String getVersion() {
                    return GradleVersion.current().getVersion();
                }
            };
        }
    }
}
