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

package org.gradle.api.internal.impldeps;

import org.gradle.api.internal.file.collections.MinimalFileSet;

import java.io.File;
import java.util.Collections;
import java.util.Set;

public class GradleImplDepsRelocatedJar implements MinimalFileSet {

    private final String displayName;
    private final File outputJar;

    public GradleImplDepsRelocatedJar(String displayName, File outputJar) {
        this.displayName = displayName;
        this.outputJar = outputJar;
    }

    @Override
    public Set<File> getFiles() {
        return Collections.singleton(outputJar);
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }
}