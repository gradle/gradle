/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.internal;

import org.gradle.api.Describable;
import org.gradle.api.internal.project.ProjectIdentity;
import org.gradle.api.internal.project.ProjectState;
import org.gradle.internal.model.ModelContainer;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.util.Path;
import org.jspecify.annotations.Nullable;

/**
 * Models and controls access to an abstract set of related mutable objects.
 * <p>
 * A context may have an identity, meaning objects within that context are
 * identifiable and addressable within a build tree. Or, they may be anonymous,
 * meaning the domain has no unique identity and therefore the objects within
 * it have no absolute identity within the build tree.
 */
@ServiceScope(Scope.Project.class)
public interface DomainObjectContext extends Describable {

    /**
     * Return the identity of this context within a build tree. Null for
     * contexts that have no unique identity.
     */
    @Nullable Path getIdentityPath();

    /**
     * The identity of the build that this context belongs to.
     */
    Path getBuildPath();

    /**
     * If this context represents a project, its identity.
     */
    @Nullable ProjectIdentity getProjectIdentity();

    /**
     * If this context represents a project, the project state.
     */
    @Nullable ProjectState getProjectState();

    /**
     * The container that holds the model for this context, to allow synchronized access to the model.
     */
    ModelContainer<?> getModel();

    /**
     * Whether the context is a script.
     *
     * Some objects are associated with a script, that is associated with a domain object.
     */
    boolean isScript();

    /**
     * Whether the context is a root script.
     *
     * `Settings` is such a context.
     *
     * Some objects are associated with a script, that is associated with a domain object.
     */
    boolean isRootScript();

    /**
     * Indicates if the context is plugin resolution
     */
    boolean isPluginContext();

    /**
     * Returns true if the context represents a detached state, for
     * example detached dependency resolution
     */
    default boolean isDetachedState() {
        return false;
    }

}
