/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.problems.internal;

import com.google.common.collect.ImmutableList;
import org.gradle.operations.problems.DocumentationLink;
import org.gradle.operations.problems.FileLocation;
import org.gradle.operations.problems.LineInFileLocation;
import org.gradle.operations.problems.OffsetInFileLocation;
import org.gradle.operations.problems.ProblemDefinition;
import org.gradle.operations.problems.ProblemGroup;
import org.gradle.operations.problems.ProblemLocation;
import org.gradle.operations.problems.ProblemUsageProgressDetails;

import javax.annotation.Nullable;
import java.util.List;

public class DefaultProblemProgressDetails implements ProblemProgressDetails, ProblemUsageProgressDetails {
    private final InternalProblem problem;

    public DefaultProblemProgressDetails(InternalProblem problem) {
        this.problem = problem;
    }

    public InternalProblem getProblem() {
        return problem;
    }

    @Override
    public ProblemDefinition getProblemDefinition() {
        return new DevelocityProblemDefinition(problem.getDefinition());
    }

    @Nullable
    @Override
    public String getContextualLabel() {
        return problem.getContextualLabel();
    }

    @Override
    public List<String> getSolutions() {
        return problem.getSolutions();
    }

    @Nullable
    @Override
    public String getDetails() {
        return problem.getDetails();
    }

    @Override
    public List<ProblemLocation> getLocations() {
        ImmutableList.Builder<ProblemLocation> builder = ImmutableList.builder();
        for (org.gradle.api.problems.ProblemLocation location : problem.getOriginLocations()) {
            ProblemLocation develocityLocation = convertToLocation(location);
            if (develocityLocation != null) {
                builder.add(develocityLocation);
            }
        }
        return builder.build();
    }

    @Nullable
    private static ProblemLocation convertToLocation(final org.gradle.api.problems.ProblemLocation location) {
        if (location instanceof org.gradle.api.problems.FileLocation) {
            if (location instanceof org.gradle.api.problems.LineInFileLocation) {
                return new DevelocityLineInFileLocation((org.gradle.api.problems.LineInFileLocation) location);
            } else if (location instanceof org.gradle.api.problems.OffsetInFileLocation) {
                return new DevelocityOffsetInFileLocation((org.gradle.api.problems.OffsetInFileLocation) location);
            } else {
                return new DevelocityFileLocation((org.gradle.api.problems.FileLocation) location);
            }
        } else if (location instanceof org.gradle.api.problems.internal.TaskPathLocation) {
            return new DevelocityTaskPathLocation((org.gradle.api.problems.internal.TaskPathLocation) location);
        } else if (location instanceof org.gradle.api.problems.internal.PluginIdLocation) {
            PluginIdLocation pluginIdLocation = (PluginIdLocation) location;
            if (pluginIdLocation.getPluginId() != null) {
                return new DevelocityPluginIdLocation(pluginIdLocation.getPluginId());
            } else {
                // Ignore "empty" plugin id locations
                return null;
            }
        } else {
            return new DevelocityProblemLocation(location);
        }
    }

    @Nullable
    @Override
    public Throwable getFailure() {
        return problem.getException() == null ? null : problem.getException();
    }

    private static class DevelocityProblemDefinition implements ProblemDefinition {
        private final org.gradle.api.problems.ProblemDefinition definition;

        public DevelocityProblemDefinition(org.gradle.api.problems.ProblemDefinition definition) {
            this.definition = definition;
        }

        @Override
        public String getId() {
            return definition.getId().getName();
        }

        @Override
        public String getDisplayName() {
            return definition.getId().getDisplayName();
        }

        @Override
        public List<ProblemGroup> getGroup() {
            ImmutableList.Builder<ProblemGroup> builder = ImmutableList.builder();
            final org.gradle.api.problems.ProblemId problemId = definition.getId();
            org.gradle.api.problems.ProblemGroup currentGroup = problemId.getGroup();
            while (currentGroup != null) {
                builder.add(new DevelocityProblemGroup(currentGroup));
                currentGroup = currentGroup.getParent();
            }
            return builder.build();
        }

        @Override
        public String getSeverity() {
            return definition.getSeverity().name();
        }

        @Nullable
        @Override
        public DocumentationLink getDocumentationLink() {
            InternalDocLink documentationLink = (InternalDocLink) definition.getDocumentationLink();
            return documentationLink == null ? null : new DevelocityDocumentationLink(documentationLink);
        }

        private static class DevelocityProblemGroup implements ProblemGroup {
            private final org.gradle.api.problems.ProblemGroup currentGroup;

            public DevelocityProblemGroup(org.gradle.api.problems.ProblemGroup currentGroup) {this.currentGroup = currentGroup;}

            @Override
            public String getId() {
                return currentGroup.getName();
            }

            @Override
            public String getDisplayName() {
                return currentGroup.getDisplayName();
            }
        }

        private static class DevelocityDocumentationLink implements DocumentationLink {
            private final InternalDocLink documentationLink;

            public DevelocityDocumentationLink(InternalDocLink documentationLink) {this.documentationLink = documentationLink;}

            @Nullable
            @Override
            public String getUrl() {
                return documentationLink.getUrl();
            }

            @Nullable
            @Override
            public String getConsultDocumentationMessage() {
                return documentationLink.getConsultDocumentationMessage();
            }
        }
    }

    private static class DevelocityProblemLocation implements ProblemLocation {
        private final org.gradle.api.problems.ProblemLocation location;

        public DevelocityProblemLocation(org.gradle.api.problems.ProblemLocation location) {this.location = location;}

        @Override
        public String getDisplayName() {
            return location.toString();
        }
    }

    private static class DevelocityFileLocation implements FileLocation {

        private final org.gradle.api.problems.FileLocation location;

        public DevelocityFileLocation(org.gradle.api.problems.FileLocation location) {
            this.location = location;
        }

        @Override
        public String getDisplayName() {
            return location.toString();
        }

        @Override
        public String getPath() {
            return location.getPath();
        }
    }

    private static class DevelocityLineInFileLocation extends DevelocityFileLocation implements LineInFileLocation {
        private final org.gradle.api.problems.LineInFileLocation lineInFileLocation;

        public DevelocityLineInFileLocation(org.gradle.api.problems.LineInFileLocation lineInFileLocation) {
            super(lineInFileLocation);
            this.lineInFileLocation = lineInFileLocation;
        }

        @Override
        public int getLine() {
            return lineInFileLocation.getLine();
        }

        @Nullable
        @Override
        public Integer getColumn() {
            return lineInFileLocation.getColumn();
        }

        @Nullable
        @Override
        public Integer getLength() {
            return lineInFileLocation.getLength();
        }
    }

    private static class DevelocityOffsetInFileLocation extends DevelocityFileLocation implements OffsetInFileLocation {

        private final org.gradle.api.problems.OffsetInFileLocation offsetInFileLocation;

        public DevelocityOffsetInFileLocation(org.gradle.api.problems.OffsetInFileLocation offsetInFileLocation) {
            super(offsetInFileLocation);
            this.offsetInFileLocation = offsetInFileLocation;
        }

        @Override
        public int getOffset() {
            return offsetInFileLocation.getOffset();
        }

        @Override
        public int getLength() {
            return offsetInFileLocation.getLength();
        }
    }

    private static class DevelocityTaskPathLocation implements org.gradle.operations.problems.TaskPathLocation {

        private final org.gradle.api.problems.internal.TaskPathLocation taskPathLocation;

        public DevelocityTaskPathLocation(org.gradle.api.problems.internal.TaskPathLocation taskPathLocation) {
            this.taskPathLocation = taskPathLocation;
        }

        @Override
        public String getBuildPath() {
            return ":";
        }

        @Override
        public String getPath() {
            return taskPathLocation.getBuildTreePath();
        }

        @Override
        public String getDisplayName() {
            // TODO: toString doesn't work for locations right now
            return taskPathLocation.toString();
        }
    }

    private static class DevelocityPluginIdLocation implements org.gradle.operations.problems.PluginIdLocation {

        private final String pluginId;

        public DevelocityPluginIdLocation(String pluginId) {
            this.pluginId = pluginId;
        }

        @Override
        public String getPluginId() {
            return pluginId;
        }

        @Override
        public String getDisplayName() {
            return pluginId;
        }
    }
}
