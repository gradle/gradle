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
package org.gradle.language.base.internal.model;

import org.gradle.api.Project;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.LibraryBinaryIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.component.local.model.DefaultLibraryBinaryIdentifier;
import org.gradle.internal.component.local.model.DefaultLocalComponentMetaData;

import java.util.Collections;

public class DefaultLibraryLocalComponentMetaData extends DefaultLocalComponentMetaData {
    private final static String VERSION = "<local component>";

    private DefaultLibraryLocalComponentMetaData(ModuleVersionIdentifier id, ComponentIdentifier componentIdentifier) {
        super(id, componentIdentifier, Project.DEFAULT_STATUS);
    }

    public static DefaultLibraryLocalComponentMetaData newMetaData(LibraryBinaryIdentifier componentId, TaskDependency buildDependencies) {
        ModuleVersionIdentifier id = new DefaultModuleVersionIdentifier(
            componentId.getProjectPath(), componentId.getLibraryName(), VERSION
        );
        DefaultLibraryLocalComponentMetaData metaData = new DefaultLibraryLocalComponentMetaData(id, componentId);
        metaData.addConfiguration(
            DefaultLibraryBinaryIdentifier.CONFIGURATION_NAME,
            String.format("Request metadata: %s", componentId.getDisplayName()),
            Collections.<String>emptySet(),
            Collections.singleton(DefaultLibraryBinaryIdentifier.CONFIGURATION_NAME),
            true,
            true,
            buildDependencies);
        return metaData;
    }
}
