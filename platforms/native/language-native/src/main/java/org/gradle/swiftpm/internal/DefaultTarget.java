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

import com.google.common.collect.ImmutableSet;

import javax.annotation.Nullable;
import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class DefaultTarget implements Serializable {
    private final String name;
    private final File path;
    private final Collection<File> sourceFiles;
    private final List<String> requiredTargets = new ArrayList<String>();
    private final List<String> requiredProducts = new ArrayList<String>();
    private File publicHeaderDir;

    public DefaultTarget(String name, File path, Iterable<File> sourceFiles) {
        this.name = name;
        this.path = path;
        this.sourceFiles = ImmutableSet.copyOf(sourceFiles);
    }

    public String getName() {
        return name;
    }

    public File getPath() {
        return path;
    }

    public Collection<File> getSourceFiles() {
        return sourceFiles;
    }

    @Nullable
    public File getPublicHeaderDir() {
        return publicHeaderDir;
    }

    public void setPublicHeaderDir(File publicHeaderDir) {
        this.publicHeaderDir = publicHeaderDir;
    }

    public Collection<String> getRequiredTargets() {
        return requiredTargets;
    }

    public Collection<String> getRequiredProducts() {
        return requiredProducts;
    }

}


