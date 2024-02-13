/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.internal.initialization.transform;

import com.google.common.collect.Ordering;
import org.gradle.api.artifacts.transform.InputArtifact;
import org.gradle.api.artifacts.transform.TransformAction;
import org.gradle.api.artifacts.transform.TransformOutputs;
import org.gradle.api.artifacts.transform.TransformParameters;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.internal.classpath.ClasspathWalker;
import org.gradle.internal.file.FileException;
import org.gradle.internal.file.Stat;
import org.gradle.internal.vfs.FileSystemAccess;
import org.gradle.work.DisableCachingByDefault;
import org.objectweb.asm.ClassReader;

import javax.inject.Inject;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static com.google.common.collect.ImmutableSortedSet.toImmutableSortedSet;

/**
 * TODO: This class has similar implementation in build-logic/packaging/src/main/kotlin/gradlebuild/instrumentation/transforms/CollectDirectClassSuperTypesTransform.kt.
 *  We could reuse the same class at some point.
 */
@DisableCachingByDefault(because = "Not worth caching.")
public abstract class CollectDirectClassSuperTypesTransform implements TransformAction<TransformParameters.None> {

    private static final Predicate<String> ACCEPTED_TYPES = type -> type != null && !type.startsWith("java/lang");
    public static final String SUPER_TYPES_MARKER_FILE_NAME = ".gradle-super-types.marker";
    public static final String DIRECT_SUPER_TYPES_SUFFIX = ".direct-super-types";
    public static final String FILE_HASH_PROPERTY_NAME = "-hash-";

    @Inject
    protected abstract ObjectFactory getObjects();

    @PathSensitive(PathSensitivity.NAME_ONLY)
    @InputArtifact
    public abstract Provider<FileSystemLocation> getInput();

    @Override
    public void transform(TransformOutputs outputs) {
        try {
            // We cannot inject internal services in to the transform directly, but we can create them via object factory
            InjectedInstrumentationServices services = getObjects().newInstance(InjectedInstrumentationServices.class);
            ClasspathWalker walker = services.getClasspathWalker();
            File inputFile = getInput().get().getAsFile();
            Map<String, Set<String>> superTypes = new TreeMap<>();
            try {
                walker.visit(inputFile, entry -> {
                    if (entry.getName().endsWith(".class")) {
                        ClassReader reader = new ClassReader(entry.getContent());
                        String className = reader.getClassName();
                        Set<String> classSuperTypes = getSuperTypes(reader);
                        if (!classSuperTypes.isEmpty()) {
                            superTypes.put(className, classSuperTypes);
                        }
                    }
                });
            } catch (FileException ignored) {
                // We support badly formatted jars on the build classpath
                // see: https://github.com/gradle/gradle/issues/13816
                return;
            }

            File outputDir = outputs.dir("supertypes");
            File output = new File(outputDir, inputFile.getName() + DIRECT_SUPER_TYPES_SUFFIX);
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(output))) {
                String hash = services.getFileSystemAccess().read(inputFile.getAbsolutePath()).getHash().toString();
                writer.write(FILE_HASH_PROPERTY_NAME + "=" + hash + "\n");
                for (Map.Entry<String, Set<String>> entry : superTypes.entrySet()) {
                    writer.write(entry.getKey() + "=" + String.join(",", entry.getValue()) + "\n");
                }
            }

            // Mark the folder so we know that this is a folder with super types files
            // This is currently used just to not delete the folders for performance testing
            new File(outputDir, SUPER_TYPES_MARKER_FILE_NAME).createNewFile();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Set<String> getSuperTypes(ClassReader reader) {
        return Stream.concat(Stream.of(reader.getSuperName()), Stream.of(reader.getInterfaces()))
            .filter(ACCEPTED_TYPES)
            .collect(toImmutableSortedSet(Ordering.natural()));
    }

    static class InjectedInstrumentationServices {

        private final ClasspathWalker classpathWalker;
        private final FileSystemAccess fileSystemAccess;

        @Inject
        public InjectedInstrumentationServices(Stat stat, FileSystemAccess fileSystemAccess) {
            this.classpathWalker = new ClasspathWalker(stat);
            this.fileSystemAccess = fileSystemAccess;
        }

        public ClasspathWalker getClasspathWalker() {
            return classpathWalker;
        }

        public FileSystemAccess getFileSystemAccess() {
            return fileSystemAccess;
        }
    }
}
