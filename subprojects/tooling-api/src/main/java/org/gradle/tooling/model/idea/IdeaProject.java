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
 * Represents the information about the IDEA project.
 *
 * @since 1.0-milestone-5
 */
public interface IdeaProject extends HierarchicalElement, Element {

    /**
     * Returns the name of the JDK.
     *
     * @return The name of the JDK.
     */
    String getJdkName();

    /**
     * Returns the language level to use within the current project.
     *
     * @return The language level to use within the current project.
     */
    IdeaLanguageLevel getLanguageLevel();

    /**
     * Returns the modules of this IDEA project. Most projects have at least one module.
     * Alias for {@link #getModules()}.
     *
     * @return The modules of this IDEA project.
     */
    DomainObjectSet<? extends IdeaModule> getChildren();

    /**
     * Returns the modules of this IDEA project. Most projects have at least one module.
     * Alias for {@link #getChildren()}.
     *
     * @return The modules of this IDEA project.
     */
    DomainObjectSet<? extends IdeaModule> getModules();
}