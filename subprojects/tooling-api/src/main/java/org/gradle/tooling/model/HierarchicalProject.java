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
 * Represents a project which belongs to some hierarchy.
 */
public interface HierarchicalProject extends Project {
    /**
     * Returns the parent project of this project, if any.
     *
     * @return The parent, or null if this project has no parent.
     */
    HierarchicalProject getParent();

    /**
     * Returns the child projects of this project.
     *
     * @return The child projects. Returns an empty set if this project has no children.
     */
    DomainObjectSet<? extends HierarchicalProject> getChildren();
}
