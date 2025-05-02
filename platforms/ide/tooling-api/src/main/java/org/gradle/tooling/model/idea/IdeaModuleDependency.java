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

package org.gradle.tooling.model.idea;

/**
 * Dependency on a module in a project.
 *
 * @since 1.0-milestone-5
 */
public interface IdeaModuleDependency extends IdeaDependency {

    /**
     * Returns the name of the target module
     *
     * @return target module name
     * @since 3.1
     */
    String getTargetModuleName();
}
