/*
 * Copyright 2007 the original author or authors.
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
package org.gradle.api.artifacts;

import org.gradle.api.Action;

/**
 * <p>An {@code ExternalDependency} is a {@link Dependency} on a source outside the current project hierarchy.</p>
 */
public interface ExternalDependency extends ModuleDependency, ModuleVersionSelector {

    /**
     * Returns whether or not the version of this dependency should be enforced in the case of version conflicts.
     */
    boolean isForce();

    /**
     * {@inheritDoc}
     */
    @Override
    ExternalDependency copy();

    /**
     * Configures the version constraint for this dependency.
     * @param configureAction the configuration action for the module version
     * @since 4.4
     */
    void version(Action<? super MutableVersionConstraint> configureAction);

    /**
     * Returns the version constraint to be used during selection.
     * @return the version constraint
     *
     * @since 4.4
     */
    VersionConstraint getVersionConstraint();
}
