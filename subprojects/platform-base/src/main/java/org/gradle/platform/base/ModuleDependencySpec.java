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
import org.gradle.api.Nullable;

/**
 * A dependency onto a module.
 */
@Incubating
public interface ModuleDependencySpec extends DependencySpec {

    /**
     * The group of the module this dependency specification refers to.
     *
     * @return the module group name
     */
    String getGroup();

    /**
     * The name of the module this dependency specification refers to.
     *
     * @return the module name
     */
    String getName();

    /**
     * The version range of the module this dependency specification refers to.
     *
     * @return the module version range
     */
    @Nullable
    String getVersion();
}
