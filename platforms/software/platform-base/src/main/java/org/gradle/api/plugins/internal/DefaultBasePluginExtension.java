/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.plugins.internal;

import org.gradle.api.Project;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.plugins.BasePluginExtension;
import org.gradle.api.provider.Property;

public class DefaultBasePluginExtension implements BasePluginExtension {

    private final DirectoryProperty distsDirectory;
    private final DirectoryProperty libsDirectory;
    private final Property<String> archivesName;

    public DefaultBasePluginExtension(Project project) {
        this.distsDirectory = project.getObjects().directoryProperty();
        this.libsDirectory = project.getObjects().directoryProperty();
        this.archivesName = project.getObjects().property(String.class);
    }

    @Override
    public DirectoryProperty getDistsDirectory() {
        return distsDirectory;
    }

    @Override
    public DirectoryProperty getLibsDirectory() {
        return libsDirectory;
    }

    @Override
    public Property<String> getArchivesName() {
        return archivesName;
    }
}
