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

package org.gradle.api.internal.changedetection.state;

import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.RelativePath;
import org.gradle.api.internal.file.collections.SingletonFileTree;

class DefaultFileDetails implements FileDetails {
    final String path;
    final FileType type;
    final FileTreeElement details;

    DefaultFileDetails(String path, FileType type, FileTreeElement details) {
        this.path = path;
        this.type = type;
        this.details = details;
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public String getName() {
        return details.getFile().getName();
    }

    @Override
    public boolean isRoot() {
        return details instanceof SingletonFileTree.SingletonFileVisitDetails;
    }

    @Override
    public RelativePath getRelativePath() {
        return details.getRelativePath();
    }

    @Override
    public FileType getType() {
        return type;
    }
}
