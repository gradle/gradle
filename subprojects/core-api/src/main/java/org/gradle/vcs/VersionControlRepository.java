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

package org.gradle.vcs;

import org.gradle.api.Incubating;

/**
 * Represents the details about a particular VCS repository that may produce zero or more components that can be used during dependency resolution.
 *
 * @since 4.10
 */
@Incubating
public interface VersionControlRepository {
    /**
     * Declares that this repository produces (or may produce) the given module.
     *
     * @param module The module identity, in "group:module" format.
     */
    void producesModule(String module);
}
