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

import java.io.Serializable;

public abstract class VersionDetails implements Serializable {
    public static VersionDetails from(String version) {
        return from(GradleVersion.version(version));
    }

    public static VersionDetails from(GradleVersion version) {
        if (version.getBaseVersion().compareTo(GradleVersion.version("5.1")) >= 0) {
            return new R51VersionDetails(version.getVersion());
        }
        if (version.getBaseVersion().compareTo(GradleVersion.version("4.8")) >= 0) {
            return new R48VersionDetails(version.getVersion());
        }
        if (version.getBaseVersion().compareTo(GradleVersion.version("4.4")) >= 0) {
            return new R44VersionDetails(version.getVersion());
        }
        if (version.getBaseVersion().compareTo(GradleVersion.version("3.5")) >= 0) {
            return new R35VersionDetails(version.getVersion());
        }
        if (version.getBaseVersion().compareTo(GradleVersion.version("2.8")) >= 0) {
            return new R28VersionDetails(version.getVersion());
        }
        return new R26VersionDetails(version.getVersion());
    }

    private final String providerVersion;

    protected VersionDetails(String version) {
        providerVersion = version;
    }

    public String getVersion() {
        return providerVersion;
    }

    /**
     * Returns true if this provider may support the given model type.
     *
     * Returns false if it is known that the provider does not support the given model type
     * and <em>should not</em> be asked to provide it.
     */
    public boolean maySupportModel(Class<?> modelType) {
        return false;
    }

    public boolean supportsEnvironmentVariablesCustomization() {
        return false;
    }

    public boolean supportsRunTasksBeforeExecutingAction() {
        return false;
    }

    public boolean supportsParameterizedToolingModels() {
        return false;
    }

    public boolean supportsRunPhasedActions() {
        return false;
    }

    public boolean supportsPluginClasspathInjection() {
        return false;
    }

    /**
     * Returns true if this provider correctly implements the protocol contract wrt exceptions thrown on cancel
     */
    public boolean honorsContractOnCancel() {
        return false;
    }

    private static class R26VersionDetails extends VersionDetails {
        R26VersionDetails(String version) {
            super(version);
        }

        @Override
        public boolean maySupportModel(Class<?> modelType) {
            return true;
        }
    }

    private static class R28VersionDetails extends R26VersionDetails {
        R28VersionDetails(String version) {
            super(version);
        }

        @Override
        public boolean supportsPluginClasspathInjection() {
            return true;
        }
    }

    private static class R35VersionDetails extends R28VersionDetails {
        R35VersionDetails(String version) {
            super(version);
        }

        @Override
        public boolean supportsEnvironmentVariablesCustomization() {
            return true;
        }

        @Override
        public boolean supportsRunTasksBeforeExecutingAction() {
            return true;
        }
    }

    private static class R44VersionDetails extends R35VersionDetails {
        R44VersionDetails(String version) {
            super(version);
        }

        @Override
        public boolean supportsParameterizedToolingModels() {
            return true;
        }
    }

    private static class R48VersionDetails extends R44VersionDetails {
        R48VersionDetails(String version) {
            super(version);
        }

        @Override
        public boolean supportsRunPhasedActions() {
            return true;
        }
    }

    private static class R51VersionDetails extends R48VersionDetails {
        R51VersionDetails(String version) {
            super(version);
        }

        @Override
        public boolean honorsContractOnCancel() {
            return true;
        }
    }
}
