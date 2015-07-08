/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.platform.base;

import org.gradle.api.*;
import org.gradle.internal.HasInternalProtocol;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.model.ModelMap;

/**
 * Represents a binary artifact that is the result of building a project component.
 */
@Incubating @HasInternalProtocol
public interface BinarySpec extends BuildableModelElement, Named {
    /**
     * Returns a human-consumable display name for this binary.
     */
    String getDisplayName();

    /**
     * Can this binary be built in the current environment?
     */
    boolean isBuildable();

    /**
     * The source sets used to compile this binary.
     *
     * @deprecated This method is replaced with {@link #getInputs()}.
     */
    @Deprecated
    DomainObjectSet<LanguageSourceSet> getSource();

    /**
     * The sources owned by this binary.
     *
     * @return the sources owned by the binary.
     */
    ModelMap<LanguageSourceSet> getSources();

    /**
     * Returns all inputs of the binary. This includes source sets owned by the binary,
     * and other source sets created elsewhere (e.g. inherited from the binary's component).
     *
     * @return all inputs of the binary.
     */
    DomainObjectSet<LanguageSourceSet> getInputs();

    /**
     * Configures the source sets used to build this binary.
     * @param action The configuration action to execute for each owned source set.
     */
    void sources(Action<? super ModelMap<LanguageSourceSet>> action);

    /**
     * The set of tasks associated with this binary.
     */
    BinaryTasksCollection getTasks();

    /**
     * Configures the tasks that build this binary.
     */
    void tasks(Action<? super BinaryTasksCollection> action);
}
