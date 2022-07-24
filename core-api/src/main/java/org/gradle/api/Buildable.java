/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api;

import org.gradle.api.tasks.TaskDependency;

/**
 * A {@code Buildable} represents an artifact or set of artifacts which are built by one or more {@link Task}
 * instances.
 */
public interface Buildable {
    /**
     * Returns a dependency which contains the tasks which build this artifact. All {@code Buildable} implementations
     * must ensure that the returned dependency object is live, so that it tracks changes to the dependencies of this
     * buildable.
     *
     * @return The dependency. Never returns null. Returns an empty dependency when this artifact is not built by any
     *         tasks.
     */
    TaskDependency getBuildDependencies();
}
