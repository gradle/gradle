/*
 * Copyright 2015 the original author or authors.
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

import org.gradle.api.Incubating;

/**
 * A builder of a {@link ModuleDependencySpec}.
 */
@Incubating
public interface ModuleDependencySpecBuilder extends DependencySpecBuilder {

    /**
     * Narrows this dependency specification down to a specific module.
     *
     * @param name the module name
     *
     * @return this instance
     */
    ModuleDependencySpecBuilder module(String name);

    /**
     * Narrows this dependency specification down to a specific group.
     *
     * @param name the group name
     *
     * @return this instance
     */
    ModuleDependencySpecBuilder group(String name);

    /**
     * Narrows this dependency specification down to a specific version range.
     *
     * @param range the version range
     *
     * @return this instance
     */
    ModuleDependencySpecBuilder version(String range);
}
