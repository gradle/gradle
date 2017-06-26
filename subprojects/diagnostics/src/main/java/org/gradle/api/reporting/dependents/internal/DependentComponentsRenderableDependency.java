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

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import org.gradle.api.artifacts.component.LibraryBinaryIdentifier;
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.RenderableDependency;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.ComponentSpec;
import org.gradle.platform.base.VariantComponentSpec;
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
        String name = DependentComponentsUtils.getBuildScopedTerseName(id);
        String description = componentSpec.getDisplayName();
        boolean buildable = true;
        if (componentSpec instanceof VariantComponentSpec) {
            // Consider variant aware components with no buildable binaries as non-buildables
            VariantComponentSpec variantComponentSpec = (VariantComponentSpec) componentSpec;
            buildable = Iterables.any(variantComponentSpec.getBinaries().values(), new Predicate<BinarySpec>() {
                @Override
                public boolean apply(BinarySpec binarySpec) {
                    return binarySpec.isBuildable();
                }
            });
        }
        boolean testSuite = false;
        return new DependentComponentsRenderableDependency(id, name, description, buildable, testSuite, children);
    }

    public static DependentComponentsRenderableDependency of(DependentBinariesResolvedResult resolvedResult) {
        LibraryBinaryIdentifier id = resolvedResult.getId();
        String name = DependentComponentsUtils.getBuildScopedTerseName(id);
        String description = id.getDisplayName();
        boolean buildable = resolvedResult.isBuildable();
        boolean testSuite = resolvedResult.isTestSuite();
        LinkedHashSet<DependentComponentsRenderableDependency> children = Sets.newLinkedHashSet();
        for (DependentBinariesResolvedResult childResolutionResult : resolvedResult.getChildren()) {
            children.add(of(childResolutionResult));
        }
        return new DependentComponentsRenderableDependency(id, name, description, buildable, testSuite, children);
    }

    private final Object id;
    private final String name;
    private final String description;
    private final boolean buildable;
    private final boolean testSuite;
    private final LinkedHashSet<? extends RenderableDependency> children;

    public DependentComponentsRenderableDependency(Object id, String name, String description, boolean buildable, boolean testSuite, LinkedHashSet<? extends RenderableDependency> children) {
        checkNotNull(id, "id must not be null");
        checkNotNull(emptyToNull(name), "name must not be null nor empty");
        this.id = id;
        this.name = name;
        this.description = emptyToNull(description);
        this.buildable = buildable;
        this.testSuite = testSuite;
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
    public ResolutionState getResolutionState() {
        return ResolutionState.RESOLVED;
    }

    @Override
    public Set<? extends RenderableDependency> getChildren() {
        return children;
    }

    public boolean isBuildable() {
        return buildable;
    }

    public boolean isTestSuite() {
        return testSuite;
    }
}
