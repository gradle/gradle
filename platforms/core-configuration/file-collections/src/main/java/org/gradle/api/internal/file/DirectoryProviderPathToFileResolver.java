/*
 * Copyright 2025 the original author or authors.
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

import org.gradle.api.file.Directory;
import org.gradle.api.provider.Provider;
import org.gradle.internal.file.PathToFileResolver;

import java.io.File;

import static org.gradle.api.internal.lambdas.SerializableLambdas.transformer;

public class DirectoryProviderPathToFileResolver implements PathToFileResolver {
    private final Provider<Directory> directoryProvider;
    private final PathToFileResolver parentResolver;

    public DirectoryProviderPathToFileResolver(Provider<Directory> directoryProvider, PathToFileResolver parentResolver) {
        this.directoryProvider = directoryProvider;
        this.parentResolver = parentResolver;
    }

    private PathToFileResolver createResolver() {
        return parentResolver.newResolver(directoryProvider.get().getAsFile());
    }

    @Override
    public File resolve(Object path) {
        return createResolver().resolve(path);
    }

    @Override
    public PathToFileResolver newResolver(File baseDir) {
        return new DirectoryProviderPathToFileResolver(
            directoryProvider.map(transformer(dir -> dir.dir(baseDir.getPath()))),
            parentResolver
        );
    }

    @Override
    public boolean canResolveRelativePath() {
        return true;
    }
}
