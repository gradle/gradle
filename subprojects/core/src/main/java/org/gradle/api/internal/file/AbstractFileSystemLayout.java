/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.api.Transformer;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileSystemLayout;
import org.gradle.api.file.RegularFile;
import org.gradle.api.internal.provider.MappingProvider;
import org.gradle.api.internal.provider.Providers;
import org.gradle.api.provider.Provider;

import java.io.File;

public abstract class AbstractFileSystemLayout implements FileSystemLayout {
    protected final FileResolver fileResolver;
    protected final FileCollectionFactory fileCollectionFactory;
    protected final FileFactory fileFactory;

    public AbstractFileSystemLayout(FileResolver fileResolver, FileCollectionFactory fileCollectionFactory, FileFactory fileFactory) {
        this.fileResolver = fileResolver;
        this.fileCollectionFactory = fileCollectionFactory;
        this.fileFactory = fileFactory;
    }

    @Override
    public Provider<RegularFile> file(Provider<File> provider) {
        return new MappingProvider<>(RegularFile.class, Providers.internal(provider), new Transformer<RegularFile, File>() {
            @Override
            public RegularFile transform(File file) {
                return fileFactory.file(fileResolver.resolve(file));
            }
        });
    }

    @Override
    public Provider<Directory> dir(Provider<File> provider) {
        return new MappingProvider<>(Directory.class, Providers.internal(provider), new Transformer<Directory, File>() {
            @Override
            public Directory transform(File file) {
                return fileFactory.dir(fileResolver.resolve(file));
            }
        });
    }
}
