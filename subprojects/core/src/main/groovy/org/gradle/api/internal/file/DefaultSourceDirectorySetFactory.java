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

package org.gradle.api.internal.file;

import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory;

public class DefaultSourceDirectorySetFactory implements SourceDirectorySetFactory {
    private final FileResolver fileResolver;
    private final DirectoryFileTreeFactory directoryFileTreeFactory;

    public DefaultSourceDirectorySetFactory(FileResolver fileResolver, DirectoryFileTreeFactory directoryFileTreeFactory) {
        this.fileResolver = fileResolver;
        this.directoryFileTreeFactory = directoryFileTreeFactory;
    }

    @Override
    public DefaultSourceDirectorySet create(String name) {
        return new DefaultSourceDirectorySet(name, fileResolver, directoryFileTreeFactory);
    }

    @Override
    public DefaultSourceDirectorySet create(String name, String displayName) {
        return new DefaultSourceDirectorySet(name, displayName, fileResolver, directoryFileTreeFactory);
    }
}
