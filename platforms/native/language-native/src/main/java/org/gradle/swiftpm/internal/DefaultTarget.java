/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.swiftpm.internal;

import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

public class DefaultTarget {
    private final String name;
    private final File path;
    private final FileCollection sourceFiles;
    private final List<String> requiredTargets = new ArrayList<String>();
    private final List<String> requiredProducts = new ArrayList<String>();
    private File publicHeaderDir;

    public DefaultTarget(String name, File path, FileCollection sourceFiles) {
        this.name = name;
        this.path = path;
        this.sourceFiles = sourceFiles;
    }

    @Input
    public String getName() {
        return name;
    }

    @Internal
    public File getPath() {
        return path;
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public FileCollection getSourceFiles() {
        return sourceFiles;
    }

    @Nullable
    @Optional
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public File getPublicHeaderDir() {
        return publicHeaderDir;
    }

    public void setPublicHeaderDir(File publicHeaderDir) {
        this.publicHeaderDir = publicHeaderDir;
    }

    @Input
    public Collection<String> getRequiredTargets() {
        return requiredTargets;
    }

    @Input
    public Collection<String> getRequiredProducts() {
        return requiredProducts;
    }

}


