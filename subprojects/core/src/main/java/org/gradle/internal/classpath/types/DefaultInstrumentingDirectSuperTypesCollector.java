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

import com.google.common.collect.ImmutableSet;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.cache.PersistentCache;
import org.gradle.internal.Either;
import org.gradle.internal.classpath.ClasspathFileHasher;
import org.gradle.internal.classpath.ClasspathWalker;
import org.gradle.internal.classpath.DefaultCachedClasspathTransformer;
import org.gradle.internal.file.FileException;
import org.gradle.internal.file.FileType;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.vfs.FileSystemAccess;
import org.objectweb.asm.ClassReader;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import static org.gradle.internal.classpath.transforms.MrJarUtils.isInUnsupportedMrJarVersionedDirectory;

/**
 * Collects the direct super types of all classes for given files and also caches values in cache.
 */
class DefaultInstrumentingDirectSuperTypesCollector implements InstrumentingDirectSuperTypesCollector {

    private static final Logger LOGGER = Logging.getLogger(DefaultInstrumentingDirectSuperTypesCollector.class);

    private final PersistentCache cache;
    private final DefaultCachedClasspathTransformer.ParallelTransformExecutor parallelExecutor;
    private final ClasspathWalker classpathWalker;
    private final FileSystemAccess fileSystemAccess;

    public DefaultInstrumentingDirectSuperTypesCollector(PersistentCache cache, DefaultCachedClasspathTransformer.ParallelTransformExecutor parallelExecutor, ClasspathWalker classpathWalker, FileSystemAccess fileSystemAccess) {
        this.cache = cache;
        this.parallelExecutor = parallelExecutor;
        this.classpathWalker = classpathWalker;
        this.fileSystemAccess = fileSystemAccess;
    }

    @Override
    public Map<String, Set<String>> visit(List<File> files, ClasspathFileHasher fileHasher) {
        List<Map<String, Set<String>>> directSuperTypes = parallelExecutor.transformAll(files, (File source, Set<HashCode> seen) -> visitClassHierarchyForFile(source, seen, fileHasher));
        return directSuperTypes.stream().flatMap(map -> map.entrySet().stream())
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, DefaultInstrumentingDirectSuperTypesCollector::concat));
    }

    private static <T> Set<T> concat(Set<T> first, Set<T> second) {
        return ImmutableSet.<T>builder()
            .addAll(first)
            .addAll(second)
            .build();
    }

    private Optional<Either<Map<String, Set<String>>, Callable<Map<String, Set<String>>>>> visitClassHierarchyForFile(File source, Set<HashCode> seen, ClasspathFileHasher fileHasher) {
        FileSystemLocationSnapshot snapshot = fileSystemAccess.read(source.getAbsoluteFile().getAbsolutePath());
        if (snapshot.getType() == FileType.Missing || !seen.add(snapshot.getHash())) {
            // Don't visit missing files or files that have already been visited
            return Optional.empty();
        }
        return Optional.of(Either.right(() -> visitClassHierarchyForFile(source, snapshot, fileHasher)));
    }

    private Map<String, Set<String>> visitClassHierarchyForFile(File source, FileSystemLocationSnapshot sourceSnapshot, ClasspathFileHasher fileHasher) throws IOException {
        Map<String, Set<String>> directSuperTypes = new HashMap<>();
        String destDirName = source.toPath().startsWith(cache.getBaseDir().toPath())
            ? source.getParentFile().getName()
            : fileHasher.hashOf(sourceSnapshot).toString();
        File destDir = new File(cache.getBaseDir(), destDirName);
        if (!destDir.exists()) {
            destDir.mkdirs();
        }
        String destFileName = sourceSnapshot.getType() == FileType.Directory ? source.getName() + ".jar" : source.getName();
        File hierachyFile = new File(destDir, destFileName + ".hierarchy");
        if (hierachyFile.isFile()) {
            return readFromFile(hierachyFile);
        }

        try {
            classpathWalker.visit(source, entry -> {
                if (entry.getName().endsWith(".class")) {
                    if (!isInUnsupportedMrJarVersionedDirectory(entry)) {
                        ClassReader classReader = new ClassReader(entry.getContent());
                        String className = classReader.getClassName();
                        registerSuperType(className, classReader.getSuperName(), directSuperTypes);
                        registerInterfaces(className, classReader.getInterfaces(), directSuperTypes);
                    }
                }
            });
        } catch (FileException e) {
            // Badly formed archive, so discard the contents and produce an empty registry
            LOGGER.debug("Malformed archive '{}'. No type hierarchy to discover.", source.getName(), e);
        }
        writeToFile(directSuperTypes, hierachyFile);
        return directSuperTypes;
    }

    private static void registerSuperType(String className, @Nullable String superType, Map<String, Set<String>> directSuperTypes) {
        if (superType != null) {
            directSuperTypes.computeIfAbsent(className, k -> new HashSet<>()).add(superType);
        }
    }

    private static void registerInterfaces(String className, String[] interfaces, Map<String, Set<String>> directSuperTypes) {
        for (String superType : interfaces) {
            directSuperTypes.computeIfAbsent(className, k -> new HashSet<>()).add(superType);
        }
    }

    public static Map<String, Set<String>> readFromFile(File file) {
        try (InputStream inputStream = Files.newInputStream(file.toPath())) {
            Map<String, Set<String>> directSuperTypes = new HashMap<>();
            Properties properties = new Properties();
            properties.load(inputStream);
            properties.forEach((k, v) -> {
                String[] values = ((String) v).split(",");
                directSuperTypes.put((String) k, new HashSet<>(Arrays.asList(values)));
            });
            return directSuperTypes;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void writeToFile(Map<String, Set<String>> directSuperTypes, File file) {
        try (OutputStream outputStream = Files.newOutputStream(file.toPath(), StandardOpenOption.CREATE)) {
            Properties properties = new Properties();
            directSuperTypes.forEach((k, v) -> properties.setProperty(k, String.join(",", v)));
            properties.store(outputStream, null);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
