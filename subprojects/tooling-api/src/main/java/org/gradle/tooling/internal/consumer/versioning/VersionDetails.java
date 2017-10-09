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

import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.gradle.tooling.model.eclipse.EclipseProject;
import org.gradle.tooling.model.eclipse.HierarchicalEclipseProject;
import org.gradle.tooling.model.gradle.BuildInvocations;
import org.gradle.tooling.model.gradle.GradleBuild;
import org.gradle.tooling.model.gradle.ProjectPublications;
import org.gradle.tooling.model.idea.BasicIdeaProject;
import org.gradle.tooling.model.idea.IdeaProject;
import org.gradle.tooling.model.internal.outcomes.ProjectOutcomes;
import org.gradle.util.GradleVersion;

import java.io.Serializable;

public abstract class VersionDetails implements Serializable {
    public static VersionDetails from(String version) {
        return from(GradleVersion.version(version));
    }

    public static VersionDetails from(GradleVersion version) {
        if (version.getBaseVersion().compareTo(GradleVersion.version("4.4")) >= 0) {
            return new R44VersionDetails(version.getVersion());
        }
        if (version.getBaseVersion().compareTo(GradleVersion.version("3.5")) >= 0) {
            return new R35VersionDetails(version.getVersion());
        }
        if (version.compareTo(GradleVersion.version("2.1")) >= 0) {
            return new R21VersionDetails(version.getVersion());
        }
        if (version.compareTo(GradleVersion.version("1.12")) >= 0) {
            return new R112VersionDetails(version.getVersion());
        }
        if (version.compareTo(GradleVersion.version("1.8")) >= 0) {
            return new R18VersionDetails(version.getVersion());
        }
        if (version.compareTo(GradleVersion.version("1.6")) >= 0) {
            return new R16VersionDetails(version.getVersion());
        }
        return new R12VersionDetails(version.getVersion());
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

    public boolean supportsCancellation() {
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

    private static class R12VersionDetails extends VersionDetails {
        public R12VersionDetails(String version) {
            super(version);
        }

        @Override
        public boolean maySupportModel(Class<?> modelType) {
            return modelType.equals(ProjectOutcomes.class)
                || modelType.equals(HierarchicalEclipseProject.class)
                || modelType.equals(EclipseProject.class)
                || modelType.equals(IdeaProject.class)
                || modelType.equals(BasicIdeaProject.class)
                || modelType.equals(BuildEnvironment.class)
                || modelType.equals(GradleProject.class)
                || modelType.equals(Void.class);
        }
    }

    private static class R16VersionDetails extends VersionDetails {
        public R16VersionDetails(String version) {
            super(version);
        }

        @Override
        public boolean maySupportModel(Class<?> modelType) {
            return modelType != BuildInvocations.class
                && modelType != ProjectPublications.class
                && modelType != GradleBuild.class;
        }
    }

    private static class R18VersionDetails extends VersionDetails {
        private R18VersionDetails(String version) {
            super(version);
        }

        @Override
        public boolean maySupportModel(Class<?> modelType) {
            return modelType != ProjectPublications.class && modelType != BuildInvocations.class;
        }
    }

    private static class R112VersionDetails extends VersionDetails {
        private R112VersionDetails(String version) {
            super(version);
        }

        @Override
        public boolean maySupportModel(Class<?> modelType) {
            return true;
        }
    }

    private static class R21VersionDetails extends VersionDetails {
        public R21VersionDetails(String version) {
            super(version);
        }

        @Override
        public boolean maySupportModel(Class<?> modelType) {
            return true;
        }

        @Override
        public boolean supportsCancellation() {
            return true;
        }
    }

    private static class R35VersionDetails extends R21VersionDetails {
        public R35VersionDetails(String version) {
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
        public R44VersionDetails(String version) {
            super(version);
        }

        @Override
        public boolean supportsParameterizedToolingModels() {
            return true;
        }
    }
}
