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

package org.gradle.internal.classpath.types;

import org.gradle.cache.PersistentCache;
import org.gradle.internal.classpath.ClasspathFileTransformer;
import org.gradle.internal.classpath.ClasspathWalker;
import org.gradle.internal.classpath.DefaultCachedClasspathTransformer.Convert;
import org.gradle.internal.classpath.DefaultCachedClasspathTransformer.ParallelTransformExecutor;
import org.gradle.internal.classpath.InstrumentingClasspathFileTransformer;
import org.gradle.internal.vfs.FileSystemAccess;

import java.io.File;
import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class DefaultInstrumentingTypeRegistryFactory implements InstrumentingTypeRegistryFactory {

    private final InstrumentingTypeRegistry gradleCoreInstrumentingRegistry;
    private final InstrumentingDirectSuperTypesCollector directSuperTypesCollector;

    public DefaultInstrumentingTypeRegistryFactory(InstrumentingTypeRegistry gradleCoreInstrumentingRegistry, PersistentCache cache, ParallelTransformExecutor parallelTransformExecutor, ClasspathWalker classpathWalker, FileSystemAccess fileSystemAccess) {
        this.gradleCoreInstrumentingRegistry = gradleCoreInstrumentingRegistry;
        this.directSuperTypesCollector = new DefaultInstrumentingDirectSuperTypesCollector(cache, parallelTransformExecutor, classpathWalker, fileSystemAccess);
    }

    @Override
    public InstrumentingTypeRegistry createFor(Collection<URL> urls, ClasspathFileTransformer transformer) {
        if (shouldReturnEmptyRegistry(transformer)) {
            return InstrumentingTypeRegistry.empty();
        }
        List<File> files = urls.stream()
            .filter(url -> url.getProtocol().equals("file"))
            .map(Convert::urlToFile)
            .collect(Collectors.toList());
        return createFor(files, transformer);
    }

    @Override
    public InstrumentingTypeRegistry createFor(List<File> files, ClasspathFileTransformer transformer) {
        if (shouldReturnEmptyRegistry(transformer)) {
            return InstrumentingTypeRegistry.empty();
        }
        Map<String, Set<String>> directSuperTypes = directSuperTypesCollector.visit(files, transformer.getFileHasher());
        return new ExternalPluginsInstrumentingTypeRegistry(directSuperTypes, gradleCoreInstrumentingRegistry);
    }

    private boolean shouldReturnEmptyRegistry(ClasspathFileTransformer transformer) {
        return gradleCoreInstrumentingRegistry.isEmpty() || !(transformer instanceof InstrumentingClasspathFileTransformer);
    }
}
