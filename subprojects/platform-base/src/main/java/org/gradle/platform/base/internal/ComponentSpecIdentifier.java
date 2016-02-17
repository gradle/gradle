/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.platform.base.internal;

import org.gradle.api.Nullable;
import org.gradle.util.Path;

/**
 * An identifier for a {@link org.gradle.platform.base.ComponentSpec}, which has a name.
 */
public interface ComponentSpecIdentifier {
    /**
     * The parent of the component, if any.
     */
    @Nullable ComponentSpecIdentifier getParent();

    /**
     * The base name of this component.
     */
    String getName();

    /**
     * A path that uniquely identifies this component within its project.
     *
     * Implementation should attempt to produce human consumable identifiers.
     */
    Path getPath();

    /**
     * Returns a child of this component, with the given name.
     */
    ComponentSpecIdentifier child(String name);

    /**
     * Returns a name that can be used to identify this component uniquely within its project. The name belongs to a flat namespace and does not include any
     * hierarchy delimiters. As such, it can be safely used for task or file names.
     *
     * Implementation should attempt to produce a somewhat human consumable name (eg not a uuid).
     */
    String getProjectScopedName();

    // TODO:RBO Clarify what it means and what's possible to do with it.
    // TODO:RBO E.g. Can the return value always be used to resolve back to the identified component? If so, how?
    // TODO:RBO Wouldn't it be better to define a proper type for project/model paths?
    /**
     * The path of the project that contains this component.
     */
    String getProjectPath();
}
