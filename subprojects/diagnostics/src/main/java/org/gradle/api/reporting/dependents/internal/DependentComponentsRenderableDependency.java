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

package org.gradle.api.reporting.dependents.internal;

import com.google.common.collect.Sets;
import org.gradle.api.Project;
import org.gradle.api.artifacts.component.LibraryBinaryIdentifier;
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.RenderableDependency;
import org.gradle.platform.base.ComponentSpec;
import org.gradle.platform.base.internal.ComponentSpecIdentifier;
import org.gradle.platform.base.internal.ComponentSpecInternal;
import org.gradle.platform.base.internal.dependents.DependentBinariesResolvedResult;

import java.util.LinkedHashSet;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.emptyToNull;

public class DependentComponentsRenderableDependency implements RenderableDependency {

    public static DependentComponentsRenderableDependency of(ComponentSpec componentSpec, ComponentSpecInternal internalProtocol) {
        return of(componentSpec, internalProtocol, null);
    }

    public static DependentComponentsRenderableDependency of(ComponentSpec componentSpec, ComponentSpecInternal internalProtocol, LinkedHashSet<DependentComponentsRenderableDependency> children) {
        ComponentSpecIdentifier id = internalProtocol.getIdentifier();
        String name = "";
        if (!Project.PATH_SEPARATOR.equals(id.getProjectPath())) {
            name += id.getProjectPath() + Project.PATH_SEPARATOR;
        }
        name += id.getProjectScopedName();
        return new DependentComponentsRenderableDependency(
            id,
            name,
            componentSpec.getDisplayName(),
            true,
            children
        );
    }

    public static DependentComponentsRenderableDependency of(DependentBinariesResolvedResult binariesResolutionResult) {
        LibraryBinaryIdentifier id = binariesResolutionResult.getId();
        String name = "";
        if (!Project.PATH_SEPARATOR.equals(id.getProjectPath())) {
            name += id.getProjectPath() + Project.PATH_SEPARATOR;
        }
        name += id.getLibraryName() + Project.PATH_SEPARATOR + id.getVariant();
        String description = id.getDisplayName();
        LinkedHashSet<DependentComponentsRenderableDependency> children = Sets.newLinkedHashSet();
        for (DependentBinariesResolvedResult childResolutionResult : binariesResolutionResult.getChildren()) {
            children.add(of(childResolutionResult));
        }
        return new DependentComponentsRenderableDependency(id, name, description, binariesResolutionResult.isBuildable(), children);
    }

    private final Object id;
    private final String name;
    private final String description;
    private final boolean buildable;
    private final LinkedHashSet<? extends RenderableDependency> children;

    public DependentComponentsRenderableDependency(Object id, String name, String description, boolean buildable) {
        this(id, name, description, buildable, null);
    }

    public DependentComponentsRenderableDependency(Object id, String name, String description, boolean buildable, LinkedHashSet<? extends RenderableDependency> children) {
        checkNotNull(id, "id must not be null");
        checkNotNull(emptyToNull(name), "name must not be null nor empty");
        this.id = id;
        this.name = name;
        this.description = emptyToNull(description);
        this.buildable = buildable;
        this.children = children;
    }


    @Override
    public Object getId() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public boolean isResolvable() {
        return true;
    }

    @Override
    public Set<? extends RenderableDependency> getChildren() {
        return children;
    }

    public boolean isBuildable() {
        return buildable;
    }
}
