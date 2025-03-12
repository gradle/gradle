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
import org.gradle.operations.problems.ProblemSeverity;
import org.gradle.operations.problems.ProblemUsageProgressDetails;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

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
    public ProblemDefinition getDefinition() {
        return new DevelocityProblemDefinition(problem.getDefinition());
    }

    @Override
    public ProblemSeverity getSeverity() {
        switch (problem.getDefinition().getSeverity()) {
            case ADVICE:
                return ProblemSeverity.ADVICE;
            case WARNING:
                return ProblemSeverity.WARNING;
            case ERROR:
                return ProblemSeverity.ERROR;
            default:
                throw new IllegalArgumentException("Unknown severity: " + problem.getDefinition().getSeverity());
        }
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
    public List<ProblemLocation> getOriginLocations() {
        return convertProblemLocations(problem.getOriginLocations());
    }

    @Override
    public List<ProblemLocation> getContextualLocations() {
        return convertProblemLocations(problem.getContextualLocations());
    }

    @NonNull
    private ImmutableList<ProblemLocation> convertProblemLocations(List<org.gradle.api.problems.ProblemLocation> locations) {
        ImmutableList.Builder<ProblemLocation> builder = ImmutableList.builder();
        for (org.gradle.api.problems.ProblemLocation location : locations) {
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
            return convertToDevelocityFileLocation(location);
        } else if (location instanceof TaskLocation) {
            // The Develocity plugin will infer the task location from the build operation hierarchy - no need to send this contextual information
            return null;
        } else if (location instanceof org.gradle.api.problems.internal.PluginIdLocation) {
            return new DevelocityPluginIdLocation((PluginIdLocation) location);
        } else if (location instanceof org.gradle.api.problems.internal.StackTraceLocation) {
            return new DevelocityStackTraceLocation((StackTraceLocation) location);
        }
        throw new IllegalArgumentException("Unknown location type: " + location.getClass() + ", location: '" + location + "'");
    }

    @NonNull
    private static FileLocation convertToDevelocityFileLocation(org.gradle.api.problems.ProblemLocation location) {
        if (location instanceof org.gradle.api.problems.LineInFileLocation) {
            return new DevelocityLineInFileLocation((org.gradle.api.problems.LineInFileLocation) location);
        } else if (location instanceof org.gradle.api.problems.OffsetInFileLocation) {
            return new DevelocityOffsetInFileLocation((org.gradle.api.problems.OffsetInFileLocation) location);
        } else {
            return new DevelocityFileLocation((org.gradle.api.problems.FileLocation) location);
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
        public String getName() {
            return definition.getId().getName();
        }

        @Override
        public String getDisplayName() {
            return definition.getId().getDisplayName();
        }

        @Override
        public ProblemGroup getGroup() {
            return new DevelocityProblemGroup(definition.getId().getGroup());
        }

        @Nullable
        @Override
        public DocumentationLink getDocumentationLink() {
            InternalDocLink documentationLink = (InternalDocLink) definition.getDocumentationLink();
            return documentationLink == null ? null : new DevelocityDocumentationLink(documentationLink);
        }

        private static class DevelocityProblemGroup implements ProblemGroup {
            private final org.gradle.api.problems.ProblemGroup currentGroup;

            public DevelocityProblemGroup(org.gradle.api.problems.ProblemGroup currentGroup) {
                this.currentGroup = currentGroup;
            }

            @Override
            public String getName() {
                return currentGroup.getName();
            }

            @Override
            public String getDisplayName() {
                return currentGroup.getDisplayName();
            }

            @Nullable
            @Override
            public ProblemGroup getParent() {
                org.gradle.api.problems.ProblemGroup parent = currentGroup.getParent();
                return parent == null ? null : new DevelocityProblemGroup(parent);
            }
        }

        private static class DevelocityDocumentationLink implements DocumentationLink {
            private final InternalDocLink documentationLink;

            public DevelocityDocumentationLink(InternalDocLink documentationLink) {
                this.documentationLink = documentationLink;
            }

            @Override
            public String getUrl() {
                return documentationLink.getUrl();
            }
        }
    }

    private static class DevelocityFileLocation implements FileLocation {

        private final org.gradle.api.problems.FileLocation location;

        public DevelocityFileLocation(org.gradle.api.problems.FileLocation location) {
            this.location = location;
        }

        @Override
        public String getPath() {
            return location.getPath();
        }

        @Override
        public String getDisplayName() {
            return "file '" + getPath() + "'";
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
            return lineInFileLocation.getColumn() <= 0 ? null : lineInFileLocation.getColumn();
        }

        @Nullable
        @Override
        public Integer getLength() {
            return lineInFileLocation.getLength() <= 0 ? null : lineInFileLocation.getLength();
        }

        @Override
        public String getDisplayName() {
            String location = getPath() + ":" + getLine();
            if (getColumn() != null) {
                location += ":" + getColumn();
            }
            if (getLength() != null) {
                location += ":" + getLength();
            }
            return "file '" + location + "'";
        }
    }

    private static class DevelocityStackTraceLocation implements org.gradle.operations.problems.StackTraceLocation {
        private final org.gradle.api.problems.internal.StackTraceLocation stackTraceLocation;

        public DevelocityStackTraceLocation(org.gradle.api.problems.internal.StackTraceLocation stackTraceLocation) {
            this.stackTraceLocation = stackTraceLocation;
        }

        @Nullable
        @Override
        public FileLocation getFileLocation() {
            return stackTraceLocation.getFileLocation() == null
                ? null
                : convertToDevelocityFileLocation(stackTraceLocation.getFileLocation());
        }

        @Override
        public List<StackTraceElement> getStackTrace() {
            return stackTraceLocation.getStackTrace();
        }

        @Override
        public String getDisplayName() {
            return "stack trace location " + stackTraceLocation.getFileLocation();
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

        @Override
        public String getDisplayName() {
            return "offset in file '" + getPath() + ":" + getOffset() + ":" + getLength() + "'";
        }
    }

    private static class DevelocityPluginIdLocation implements org.gradle.operations.problems.PluginIdLocation {

        private final PluginIdLocation pluginId;

        public DevelocityPluginIdLocation(PluginIdLocation pluginId) {
            this.pluginId = pluginId;
        }

        @Override
        public String getPluginId() {
            return pluginId.getPluginId();
        }

        @Override
        public String getDisplayName() {
            return "plugin '" + pluginId.getPluginId() + "'";
        }
    }
}
