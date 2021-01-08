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

import java.io.Serializable;

/**
 * The identifier of a module version.
 */
public interface ModuleVersionIdentifier extends Serializable {

    /**
     * The version of the module
     *
     * @return module version
     */
    String getVersion();

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
     * Returns the {@link ModuleIdentifier} containing the group and the name of this module.
     * Contains the same information as {@link #getGroup()} and {@link #getVersion()}
     *
     * @return the module identifier
     * @since 1.4
     */
    ModuleIdentifier getModule();
}
