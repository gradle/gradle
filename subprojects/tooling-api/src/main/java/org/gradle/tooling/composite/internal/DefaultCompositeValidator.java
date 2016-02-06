/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.tooling.composite.internal;

import org.gradle.api.specs.Spec;
import org.gradle.internal.SystemProperties;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.composite.GradleConnection;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.gradle.util.CollectionUtils;
import org.gradle.util.VersionNumber;

import java.io.File;
import java.util.Set;

public class DefaultCompositeValidator implements CompositeValidator {
    private String lastUnsatisfiedReason = "";

    @Override
    public String whyUnsatisfied() {
        return lastUnsatisfiedReason;
    }

    @Override
    public boolean isSatisfiedBy(GradleConnection connection) {
        StringBuilder reason = new StringBuilder();
        try {
            boolean meetsMinimumVersion = new VerifyMinimumGradleVersion(reason).isSatisfiedBy(getBuildEnvironments(connection));
            boolean hasNoOverlappingProjects = new VerifyNoOverlappingProjects(reason).isSatisfiedBy(getGradleProjects(connection));
            return meetsMinimumVersion && hasNoOverlappingProjects;
        } catch (GradleConnectionException e) {
            // TODO: Better error message
            reason.append("Could not validate composite.");
            return false;
        } finally {
            lastUnsatisfiedReason = reason.toString();
        }
    }

    private Set<BuildEnvironment> getBuildEnvironments(GradleConnection connection) {
        return connection.getModels(BuildEnvironment.class);
    }

    private Set<GradleProject> getGradleProjects(GradleConnection connection) {
        return connection.getModels(GradleProject.class);
    }

    // Verify Gradle participants >= 1.0
    final static class VerifyMinimumGradleVersion implements Spec<Set<BuildEnvironment>> {
        private final StringBuilder reason;
        private final VersionNumber minimumVersion = VersionNumber.parse("1.0");

        VerifyMinimumGradleVersion(StringBuilder reason) {
            this.reason = reason;
        }

        @Override
        public boolean isSatisfiedBy(Set<BuildEnvironment> buildEnvironments) {
            return CollectionUtils.every(buildEnvironments, new Spec<BuildEnvironment>() {
                @Override
                public boolean isSatisfiedBy(BuildEnvironment buildEnvironment) {
                    boolean meetsMinimum = minimumVersion.compareTo(VersionNumber.parse(buildEnvironment.getGradle().getGradleVersion())) < 0;
                    if (!meetsMinimum) {
                        reason.append("Composite builds require Gradle 1.0 or newer. A project is configured to use ").
                            append(buildEnvironment.getGradle().getGradleVersion()).
                            append(", which is too old to be used in a composite.").
                            append(SystemProperties.getInstance().getLineSeparator());
                    }
                    return meetsMinimum;
                }
            });
        }
    }

    /**
     * Verify no projects are overlapping.
     * Overlapping means two projects have the same project directory.
     *
     * Project1 - path/to/project
     * Project2 - path/to/project
     * Project1 and 2 are overlapping.
     *
     * Project1 - path/to/project
     * Project2 - path/to/project/another
     * Project1 and 2 are NOT overlapping.
     *
     * Project1 - path/to/project
     * Project1:another - path/to/project/another
     * Project2 - path/to/project/another
     * Project1:another and 2 are overlapping.
     */
    final static class VerifyNoOverlappingProjects implements Spec<Set<GradleProject>> {
        private final StringBuilder reason;

        VerifyNoOverlappingProjects(StringBuilder reason) {
            this.reason = reason;
        }

        private GradleProject findRoot(GradleProject gradleProject) {
            if (gradleProject.getParent()==null) {
                return gradleProject;
            }
            return findRoot(gradleProject.getParent());
        }

        private boolean doesProjectOverlapWithRoots(final GradleProject project, Set<GradleProject> roots) {
            return CollectionUtils.any(roots, new Spec<GradleProject>() {
                @Override
                public boolean isSatisfiedBy(GradleProject root) {
                    if (root == project) {
                        // cannot overlap with itself
                        return false;
                    }
                    File projectDir = project.getProjectDirectory();
                    File rootDir = root.getProjectDirectory();

                    if (projectDir.equals(rootDir)) {
                        reason.append("Projects in a composite must not have overlapping project directories.").
                            append(" Project '").append(project.getName()).append("' with root (").append(findRoot(project).getProjectDirectory()).append(")").
                            append(" overlaps with project '").append(root.getName()).append("' (").append(root.getProjectDirectory()).append(").").
                            append(SystemProperties.getInstance().getLineSeparator());
                        return true;
                    }
                    return false;
                }
            });
        }

        @Override
        public boolean isSatisfiedBy(Set<GradleProject> gradleProjects) {
            final Set<GradleProject> roots = CollectionUtils.filter(gradleProjects, new Spec<GradleProject>() {
                @Override
                public boolean isSatisfiedBy(GradleProject gradleProject) {
                    return gradleProject.getParent() == null;
                }
            });

            boolean anyOverlappingDirectories = CollectionUtils.any(gradleProjects, new Spec<GradleProject>() {
                @Override
                public boolean isSatisfiedBy(GradleProject gradleProject) {
                    return doesProjectOverlapWithRoots(gradleProject, roots);
                }
            });

            return !anyOverlappingDirectories;
        }
    }
}
