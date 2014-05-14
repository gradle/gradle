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

package org.gradle.plugin.use;

import org.gradle.api.Incubating;

/**
 * The DSL for declaring plugin dependencies.
 * <p>
 * The <code>plugins { }</code> script block delegates to this type.
 * Moreover, the only code that is allowed during a <code>plugins { }</code> script block is <i>direct</i> usage of this API.
 * That is, only the methods of this type can be called on only with <i>literal</i> string arguments.
 */
// TODO expand on this Javadoc by pointing to userguide chapter on plugin mechanism when added
@Incubating
public interface PluginDependenciesSpec {

    /**
     * Add a dependency on the plugin with the given id.
     * <p>
     * <pre>
     * plugins {
     *     id "org.company.myplugin"
     * }
     * </pre>
     * Further constraints (e.g. version number) can be specified by the methods of the return value.
     * <pre>
     * plugins {
     *     id "org.company.myplugin" version "1.3"
     * }
     * </pre>
     *
     * @param id the id of the plugin to depend on
     * @return a mutable plugin dependency specification that can be used to further refine the dependency
     */
    PluginDependencySpec id(String id);

}
