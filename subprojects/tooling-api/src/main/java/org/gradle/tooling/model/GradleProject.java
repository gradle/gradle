/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.tooling.model;

/**
 * Gradle project.
 *
 * TODO SF. It would be nice to decouple gradle tasks from IDEA / Eclipse model.
 * Many things are easier if they are decoupled and it fits our present trend of decoupling gradle project from idea module & eclipse project.
 * Needs discussion. This is the first step towards this idea.
 */
public interface GradleProject extends HierarchicalElement {

    /**
     * Returns the tasks of this project.
     *
     * @return The tasks.
     */
    DomainObjectSet<? extends GradleTask> getTasks();

    /**
     * {@inheritDoc}
     */
    GradleProject getParent();

    /**
     * {@inheritDoc}
     */
    DomainObjectSet<? extends GradleProject> getChildren();

    /**
     * Returns gradle path
     *
     * @return The path.
     */
    String getPath();
}
