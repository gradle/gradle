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

package org.gradle.api.internal.changedetection.state;

import org.gradle.internal.file.FileType;

import java.io.IOException;
import java.util.Collection;

public class FileTree implements ResourceTree {
    private final Collection<FileSnapshot> descendants;

    public FileTree(Collection<FileSnapshot> descendants) {
        this.descendants = descendants;
    }

    @Override
    public void visit(ResourceWithContentsVisitor visitor) throws IOException {
        for (FileSnapshot descendant : descendants) {
            if (descendant.getType() == FileType.RegularFile) {
                RegularFileSnapshot fileSnapshot = (RegularFileSnapshot) descendant;
                visitor.visitFileSnapshot(fileSnapshot);
            }
        }
    }
}
