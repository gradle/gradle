/*
 * Copyright 2011 the original author or authors.
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

import org.gradle.util.GradleVersion;

/**
 * by Szczepan Faber, created at: 1/13/12
 */
public class VersionDetails {

    private final GradleVersion gradleVersion;
    private static final GradleVersion M5 = GradleVersion.version("1.0-milestone-5");
    private static final GradleVersion M6 = GradleVersion.version("1.0-milestone-6");
    private static final GradleVersion M7 = GradleVersion.version("1.0-milestone-7");
    private static final GradleVersion V1_1 = GradleVersion.version("1.1");

    public VersionDetails(String version) {
        gradleVersion = GradleVersion.version(version);
    }

    public String getVersion() {
        return gradleVersion.getVersion();
    }

    public boolean supportsCompleteBuildEnvironment() {
        return gradleVersion.compareTo(M7) > 0;
    }

    public boolean clientHangsOnEarlyDaemonFailure() {
        return gradleVersion.equals(M5) || gradleVersion.equals(M6);
    }

    public boolean isPostM6Model(Class<?> internalModelType) {
        return !ModelMapping.getModelsUpToM6().containsValue(internalModelType) && internalModelType != Void.class;
    }

    public boolean supportsConfiguringJavaHome() {
        return gradleVersion.compareTo(M7) > 0;
    }

    public boolean supportsConfiguringJvmArguments() {
        return gradleVersion.compareTo(M7) > 0;
    }

    public boolean supportsConfiguringStandardInput() {
        return gradleVersion.compareTo(M7) > 0;
    }

    public boolean supportsRunningTasksWhenBuildingModel() {
        return gradleVersion.compareTo(V1_1) > 0;
    }

    public boolean supportsGradleProjectModel() {
        return gradleVersion.compareTo(M5) >= 0;
    }
}