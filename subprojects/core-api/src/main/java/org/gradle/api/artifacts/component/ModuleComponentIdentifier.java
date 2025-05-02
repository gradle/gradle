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

import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.internal.scan.UsedByScanPlugin;

/**
 * An identifier for a component instance which is available as a module version.
 *
 * @since 1.10
 */
@UsedByScanPlugin
public interface ModuleComponentIdentifier extends ComponentIdentifier {
    /**
     * The module group of the component.
     *
     * @return Component group
     * @since 1.10
     */
    String getGroup();

    /**
     * The module name of the component.
     *
     * @return Component module
     * @since 1.10
     */
    String getModule();

    /**
     * The module version of the component.
     *
     * @return Component version
     * @since 1.10
     */
    String getVersion();

    /**
     * The module identifier of the component. Returns the same information
     * as {@link #getGroup()} and {@link #getModule()}.
     *
     * @return the module identifier
     *
     * @since 4.9
     */
    ModuleIdentifier getModuleIdentifier();
}

