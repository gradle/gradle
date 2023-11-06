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

package org.gradle.api.internal.initialization.transform;

import com.google.common.collect.Ordering;
import org.gradle.api.artifacts.transform.InputArtifact;
import org.gradle.api.artifacts.transform.TransformAction;
import org.gradle.api.artifacts.transform.TransformOutputs;
import org.gradle.api.artifacts.transform.TransformParameters;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Classpath;
import org.gradle.internal.classpath.ClasspathWalker;
import org.gradle.internal.file.Stat;
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
@DisableCachingByDefault(because = "Not enable yet, since original instrumentation is also not cached in build cache.")
public abstract class CollectDirectClassSuperTypesTransform implements TransformAction<TransformParameters.None> {

    private static final Predicate<String> ACCEPTED_TYPES = type -> type != null && !type.startsWith("java/lang");
    private static final String FILE_SUFFIX = ".super-types";

    @Inject
    public abstract ObjectFactory getObjects();

    @InputArtifact
    @Classpath
    public abstract Provider<FileSystemLocation> getInput();

    public void transform(TransformOutputs outputs) {
        try {
            InstrumentationServices instrumentationServices = getObjects().newInstance(InstrumentationServices.class);
            ClasspathWalker walker = instrumentationServices.getClasspathWalker();
            File inputFile = getInput().get().getAsFile();
            Map<String, Set<String>> superTypes = new TreeMap<>();
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

            File output = outputs.file(inputFile.getName() + FILE_SUFFIX);
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(output))) {
                for (Map.Entry<String, Set<String>> entry : superTypes.entrySet()) {
                    writer.write(entry.getKey() + "=" + String.join(",", entry.getValue()) + "\n");
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Set<String> getSuperTypes(ClassReader reader) {
        return Stream.concat(Stream.of(reader.getSuperName()), Stream.of(reader.getInterfaces()))
            .filter(ACCEPTED_TYPES)
            .collect(toImmutableSortedSet(Ordering.natural()));
    }

    static class InstrumentationServices {

        private final ClasspathWalker classpathWalker;

        @Inject
        public InstrumentationServices(Stat stat) {
            this.classpathWalker = new ClasspathWalker(stat);
        }

        public ClasspathWalker getClasspathWalker() {
            return classpathWalker;
        }
    }
}
