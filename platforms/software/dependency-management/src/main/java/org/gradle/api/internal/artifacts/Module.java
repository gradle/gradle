/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.artifacts;

/**
 * Represents the module identity of the component that a dependency management instance exposes.
 *
 * This is used both as the identity of the root component of a resolution, and the public identity
 * of the components that a dependency management instance exposes.
 *
 * TODO: We should migrate away from this type. Not all components have a module identity.
 */
public interface Module {

    String getGroup();

    String getName();

    String getVersion();

    String getStatus();

}
