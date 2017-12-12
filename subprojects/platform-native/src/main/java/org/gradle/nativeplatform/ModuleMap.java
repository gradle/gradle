/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.nativeplatform;

import org.gradle.api.Incubating;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;

/**
 * Represents a mapping between a module and the header files associated with the module.
 *
 * @since 4.5
 */
@Incubating
public class ModuleMap {
    private final Property<String> moduleName;
    private final ListProperty<String> publicHeaderPaths;

    public ModuleMap(Property<String> moduleName, ListProperty<String> publicHeaderPaths) {
        this.moduleName = moduleName;
        this.publicHeaderPaths = publicHeaderPaths;
    }

    /**
     * The name of the module to use for the generated module map.
     */
    @Input
    public Property<String> getModuleName() {
        return moduleName;
    }

    /**
     * The list of public header paths that should be exposed by the module.
     */
    @Input
    public ListProperty<String> getPublicHeaderPaths() {
        return publicHeaderPaths;
    }
}
