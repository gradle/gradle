/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.internal.component.local.model;

import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentSelector;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.artifacts.DefaultProjectComponentIdentifier;
import org.gradle.api.internal.artifacts.ProjectComponentIdentifierInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.util.Path;

import java.util.List;
import java.util.Objects;

public class DefaultProjectComponentSelector implements ProjectComponentSelectorInternal {
    private final BuildIdentifier buildIdentifier;
    private final Path projectPath;
    private final Path buildTreePath;
    private final String projectName;
    private final ImmutableAttributes attributes;
    private final List<Capability> requestedCapabilities;
    private String displayName;

    public DefaultProjectComponentSelector(
        BuildIdentifier buildIdentifier,
        Path buildTreePath,
        Path projectPath,
        String projectName,
        ImmutableAttributes attributes,
        List<Capability> requestedCapabilities
    ) {
        assert buildIdentifier != null : "build cannot be null";
        assert buildTreePath != null : "build tree path path cannot be null";
        assert projectPath != null : "project path cannot be null";
        assert projectName != null : "project name cannot be null";
        assert attributes != null : "attributes cannot be null";
        assert requestedCapabilities != null : "capabilities cannot be null";

        this.buildIdentifier = buildIdentifier;
        this.buildTreePath = buildTreePath;
        this.projectPath = projectPath;
        this.projectName = projectName;
        this.attributes = attributes;
        this.requestedCapabilities = requestedCapabilities;
    }

    @Override
    public String getDisplayName() {
        String prefix;
        if (Objects.equals(buildTreePath, Path.ROOT)) {
            prefix =  "root project";
        } else {
            prefix = "project";
        }
        if (displayName == null) {
            displayName = prefix + " " + buildTreePath;
        }
        return displayName;
    }

    public BuildIdentifier getBuildIdentifier() {
        return buildIdentifier;
    }

    @Override
    public String getBuildPath() {
        return buildIdentifier.getBuildPath();
    }

    @SuppressWarnings("deprecation")
    @Override
    public String getBuildName() {
        DeprecationLogger.deprecateMethod(ProjectComponentSelector.class, "getBuildName()")
            .withAdvice("Use getBuildPath() to get a unique identifier for the build.")
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(8, "build_identifier_name_and_current_deprecation")
            .nagUser();
        return DeprecationLogger.whileDisabled(buildIdentifier::getName);
    }

    @Override
    public Path getIdentityPath() {
        return buildTreePath;
    }

    @Override
    public String getProjectPath() {
        return projectPath.getPath();
    }

    public Path projectPath() {
        return projectPath;
    }

    public String getProjectName() {
        return projectName;
    }

    @Override
    public boolean matchesStrictly(ComponentIdentifier identifier) {
        assert identifier != null : "identifier cannot be null";

        if (identifier instanceof ProjectComponentIdentifier) {
            ProjectComponentIdentifierInternal projectComponentIdentifier = (ProjectComponentIdentifierInternal) identifier;
            return projectComponentIdentifier.getIdentityPath().equals(buildTreePath);
        }

        return false;
    }

    @Override
    public AttributeContainer getAttributes() {
        return attributes;
    }

    @Override
    public List<Capability> getRequestedCapabilities() {
        return requestedCapabilities;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DefaultProjectComponentSelector)) {
            return false;
        }
        DefaultProjectComponentSelector that = (DefaultProjectComponentSelector) o;
        if (!buildTreePath.equals(that.buildTreePath)) {
            return false;
        }
        if (!attributes.equals(that.attributes)) {
            return false;
        }
        return requestedCapabilities.equals(that.requestedCapabilities);
    }

    @Override
    public int hashCode() {
        return Objects.hash(buildTreePath, attributes, requestedCapabilities);
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    public static ProjectComponentSelector newSelector(
        ProjectComponentIdentifier identifier,
        ImmutableAttributes attributes,
        List<Capability> requestedCapabilities
    ) {
        DefaultProjectComponentIdentifier projectComponentIdentifier = (DefaultProjectComponentIdentifier) identifier;
        return new DefaultProjectComponentSelector(
            projectComponentIdentifier.getBuild(),
            projectComponentIdentifier.getIdentityPath(),
            projectComponentIdentifier.projectPath(),
            projectComponentIdentifier.getProjectName(),
            attributes,
            requestedCapabilities
        );
    }

    public static ProjectComponentSelector withAttributes(ProjectComponentSelector selector, ImmutableAttributes attributes) {
        DefaultProjectComponentSelector current = (DefaultProjectComponentSelector) selector;
        return new DefaultProjectComponentSelector(
            current.buildIdentifier,
            current.buildTreePath,
            current.projectPath,
            current.projectName,
            attributes,
            current.requestedCapabilities
        );
    }

    public static ProjectComponentSelector withCapabilities(ProjectComponentSelector selector, List<Capability> requestedCapabilities) {
        DefaultProjectComponentSelector current = (DefaultProjectComponentSelector) selector;
        return new DefaultProjectComponentSelector(
            current.buildIdentifier,
            current.buildTreePath,
            current.projectPath,
            current.projectName,
            current.attributes,
            requestedCapabilities
        );
    }

    public static ProjectComponentSelector withAttributesAndCapabilities(ProjectComponentSelector selector, ImmutableAttributes attributes, List<Capability> requestedCapabilities) {
        DefaultProjectComponentSelector current = (DefaultProjectComponentSelector) selector;
        return new DefaultProjectComponentSelector(
            current.buildIdentifier,
            current.buildTreePath,
            current.projectPath,
            current.projectName,
            attributes,
            requestedCapabilities
        );
    }

    public ProjectComponentIdentifier toIdentifier() {
        return new DefaultProjectComponentIdentifier(buildIdentifier, buildTreePath, projectPath, projectName);
    }
}
