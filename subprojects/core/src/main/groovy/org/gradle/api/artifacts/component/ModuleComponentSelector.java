/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.api.artifacts.component;

import org.gradle.api.Incubating;

/**
 * Criteria for selecting a component instance that is available as a module version.
 *
 * @since 1.10
 */
@Incubating
public interface ModuleComponentSelector extends ComponentSelector {
    /**
     * The group of the module to select the component from.
     *
     * @return Module group
     * @since 1.10
     */
    String getGroup();

    /**
     * The name of the module to select the component from.
     *
     * @return Module name
     */
    String getModule();

    /**
     * The version of the module to select the component from.
     *
     * @return Module version
     */
    String getVersion();
}
