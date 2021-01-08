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

package org.gradle.api.artifacts;

import javax.annotation.Nullable;

/**
 * Selects a module version.
 * If you need to change this interface, you're probably doing it wrong:
 * it is superseded by {@link org.gradle.api.artifacts.component.ModuleComponentSelector}, so check this first, and only
 * add methods here if it's for bridging.
 */
public interface ModuleVersionSelector {

    /**
     * The group of the module.
     *
     * @return module group
     */
    String getGroup();

    /**
     * The name of the module.
     *
     * @return module name
     */
    String getName();

    /**
     * The version of the module. May be null.
     *
     * @return module version
     *
     */
    @Nullable
    String getVersion();

    /**
     * To match strictly means that the given identifier needs to have
     * equal group, module name and version.
     * It does not smartly match dynamic versions,
     * e.g. '1.+' selector does not strictly match '1.2' identifier.
     *
     * @return if this selector matches exactly the given identifier.
     */
    boolean matchesStrictly(ModuleVersionIdentifier identifier);

    /**
     * The module identifier of the component. Returns the same information
     * as {@link #getGroup()} and {@link #getName()}.
     *
     * @return the module identifier
     *
     * @since 4.9
     */
    ModuleIdentifier getModule();
}
