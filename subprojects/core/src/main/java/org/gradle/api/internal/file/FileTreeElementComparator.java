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

package org.gradle.api.internal.file;

import org.gradle.api.file.FileTreeElement;

import java.util.Comparator;

// Comparator that is fast and produces stable order for FileTreeElements for hashing
public class FileTreeElementComparator implements Comparator<FileTreeElement> {
    public static final FileTreeElementComparator INSTANCE = new FileTreeElementComparator();

    private FileTreeElementComparator() {
    }

    @Override
    public int compare(FileTreeElement o1, FileTreeElement o2) {
        int compareResult = (o1.isDirectory() == o2.isDirectory()) ? 0 : (o1.isDirectory() ? 1 : -1);
        if (compareResult != 0) {
            return compareResult;
        }
        if (!o1.isDirectory() && !o2.isDirectory()) {
            compareResult = (o1.getLastModified() < o2.getLastModified()) ? -1 : ((o1.getLastModified() == o2.getLastModified()) ? 0 : 1);
            if (compareResult != 0) {
                return compareResult;
            }
            compareResult = (o1.getSize() < o2.getSize()) ? -1 : ((o1.getSize() == o2.getSize()) ? 0 : 1);
            if (compareResult != 0) {
                return compareResult;
            }
        }
        return o1.getRelativePath().compareTo(o2.getRelativePath());
    }
}
