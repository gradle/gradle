/*
 * Copyright 2018 the original author or authors.
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

import java.util.List;

/**
 * Allows a custom version lister to specify the list of versions known
 * for a specific module.
 *
 * @since 4.9
 */
public interface ComponentMetadataListerDetails {
    /**
     * Gives access to the module identifier for which the version lister should
     * return the list of versions.
     * @return the module identifier for which versions are requested
     *
     * @since 4.9
     */
    ModuleIdentifier getModuleIdentifier();

    /**
     * List the versions of the requested component.
     * @param versions the list of versions for the requested component.
     *
     * @since 4.9
     */
    void listed(List<String> versions);
}
