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
 * <p>A {@code ExternalModuleDependency} is a {@link Dependency} on a module outside the current project hierarchy.</p>
 */
public interface ExternalModuleDependency extends ExternalDependency {
    /**
     * Indicates that the given dependency can have different content for the same identifier.
     *
     * @see #setChanging(boolean)
     */
    boolean isChanging();

    /**
     * Sets the dependency as "changing" or "not changing".
     * If set to true, the dependency is marked as "changing." Gradle will periodically check the remote repository for updates, even if the local cache entry has not yet expired.
     * Defaults to false.
     *
     * @param changing if true, the dependency is considered changing and Gradle should
     * check for a change in the remote repository, even if a local entry exists. 
     * @return this
     */
    ExternalModuleDependency setChanging(boolean changing);

    /**
     * {@inheritDoc}
     */
    @Override
    ExternalModuleDependency copy();
}
