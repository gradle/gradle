/*
 * Copyright 2007-2008 the original author or authors.
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

/**
 * <p>An {@code ExternalDependency} is a {@link Dependency} on a source outside the current project hierarchy.</p>
 */
public interface ExternalDependency extends ModuleDependency, ModuleVersionSelector {
    /**
     * Returns whether or not the version of this dependency should be enforced in the case of version conflicts.
     */
    boolean isForce();

    /**
     * Sets whether or not the version of this dependency should be enforced in the case of version conflicts.
     *
     * @param force Whether to force this version or not.
     * @return this
     */
    ExternalDependency setForce(boolean force);

    /**
     * {@inheritDoc}
     */
    ExternalDependency copy();
}
