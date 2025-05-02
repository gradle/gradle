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

package org.gradle.api.internal.file.collections;

import org.gradle.api.file.DirectoryTree;
import org.gradle.api.file.RelativePath;
import org.gradle.api.internal.file.DefaultFileTreeElement;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;

import java.io.File;

public abstract class DirectoryTrees {
    private DirectoryTrees() {
    }

    public static boolean contains(FileSystem fileSystem, DirectoryTree tree, File file) {
        String prefix = tree.getDir().getAbsolutePath() + File.separator;
        if (!file.getAbsolutePath().startsWith(prefix)) {
            return false;
        }

        RelativePath path = RelativePath.parse(true, file.getAbsolutePath().substring(prefix.length()));
        return tree.getPatterns().getAsSpec().isSatisfiedBy(new DefaultFileTreeElement(file, path, fileSystem, fileSystem));
    }

}
