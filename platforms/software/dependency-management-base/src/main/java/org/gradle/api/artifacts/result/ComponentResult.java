/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.api.artifacts.result;

import org.gradle.api.artifacts.component.ComponentIdentifier;

/**
 * The result of resolving a component.
 *
 * @since 2.0
 */
public interface ComponentResult {
    /**
     * <p>Returns the identifier of this component. This can be used to uniquely identify the component within the current build, but it is not necessarily unique between
     * different builds.
     *
     * <p>The return type is declared as an opaque {@link ComponentIdentifier}, however the identifier may also implement one of the following interfaces:</p>
     *
     * <ul>
     *     <li>{@link org.gradle.api.artifacts.component.ProjectComponentIdentifier} for those component instances which are produced by the current build.</li>
     *     <li>{@link org.gradle.api.artifacts.component.ModuleComponentIdentifier} for those component instances which are found in some repository.</li>
     * </ul>
     *
     * @return the identifier of this component
     */
    ComponentIdentifier getId();
}
