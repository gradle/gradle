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

package org.gradle.tooling.model.idea;

import org.gradle.tooling.model.DomainObjectSet;
import org.gradle.tooling.model.Element;
import org.gradle.tooling.model.HierarchicalElement;

/**
 * Represents the information about the IntelliJ IDEA project
 *
 * @since 1.0-rc-1
 */
public interface IdeaProject extends HierarchicalElement, Element {

    /**
     * The name of the jdk
     *
     * @return jdk name
     */
    String getJdkName();

    /**
     * Language level to use within the current project.
     *
     * @return language level
     */
    IdeaLanguageLevel getLanguageLevel();

    /**
     * Returns modules of this idea project. Most projects have at least one module.
     * Alias to {@link #getModules()}
     *
     * @return modules
     */
    DomainObjectSet<? extends IdeaModule> getChildren();

    /**
     * Returns modules of this idea project. Most projects have at least one module.
     * Alias to {@link #getChildren()}
     *
     * @return modules
     */
    DomainObjectSet<? extends IdeaModule> getModules();
}