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
 * An identifier for a module component.
 */
@Incubating
public interface ModuleComponentIdentifier extends ComponentIdentifier {
    /**
     * The group of a component identifier.
     *
     * @return Component identifier group
     */
    String getGroup();

    /**
     * The name of a component identifier.
     *
     * @return Component identifier name
     */
    String getName();

    /**
     * The version of a component identifier.
     *
     * @return Component identifier version
     */
    String getVersion();
}
