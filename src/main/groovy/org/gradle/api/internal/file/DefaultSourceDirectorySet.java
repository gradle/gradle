/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.file;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.file.SourceSet;
import org.gradle.api.tasks.util.PatternSet;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Set;

public class DefaultSourceDirectorySet extends AbstractSourceSet implements SourceDirectorySet {
    private final PathResolvingFileCollection srcDirs;

    public DefaultSourceDirectorySet(FileResolver resolver) {
        srcDirs = new PathResolvingFileCollection(resolver);
    }

    public Set<File> getSrcDirs() {
        return srcDirs.getFiles();
    }

    protected Set<File> getExistingSourceDirs() {
        Set<File> existingSourceDirs = new LinkedHashSet<File>();
        for (File srcDir : srcDirs) {
            if (srcDir.isDirectory()) {
                existingSourceDirs.add(srcDir);
            } else if (srcDir.exists()) {
                throw new InvalidUserDataException(String.format("Source directory '%s' is not a directory.", srcDir));
            }
        }
        return existingSourceDirs;
    }

    public SourceSet srcDir(Object srcDir) {
        srcDirs.add(srcDir);
        return this;
    }

    public SourceSet srcDirs(Object... srcDirs) {
        for (Object srcDir : srcDirs) {
            this.srcDirs.add(srcDir);
        }
        return this;
    }
}
