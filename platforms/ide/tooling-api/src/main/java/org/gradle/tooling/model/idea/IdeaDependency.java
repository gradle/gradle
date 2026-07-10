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

import org.gradle.tooling.model.Dependency;

/**
 * IDEA dependency.
 *
 * @since 1.0-milestone-5
 */
public interface IdeaDependency extends Dependency {

    /**
     * scope of the current dependency. Not-<code>null</code> all the time
     *
     * @return scope
     */
    IdeaDependencyScope getScope();

    /**
     * Allows to check if current dependency is transitive, i.e. is visible to the module which depends on module that has current dependency.
     *
     * @return <code>true</code> if current dependency is transitive; <code>false</code> otherwise
     */
    boolean getExported();
}
