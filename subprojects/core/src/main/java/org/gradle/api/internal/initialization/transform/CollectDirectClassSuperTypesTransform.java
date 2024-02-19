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
import org.gradle.api.internal.initialization.transform.utils.ClassAnalysisUtils;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.internal.classpath.ClasspathWalker;
import org.gradle.internal.file.FileException;
import org.gradle.internal.io.IoConsumer;
import org.gradle.work.DisableCachingByDefault;
import org.objectweb.asm.ClassReader;

import javax.inject.Inject;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.collect.ImmutableSortedSet.toImmutableSortedSet;
import static org.gradle.api.internal.initialization.transform.utils.InstrumentationTransformUtils.DEPENDENCIES_FILE_NAME;
import static org.gradle.api.internal.initialization.transform.utils.InstrumentationTransformUtils.FILE_HASH_PROPERTY_NAME;
import static org.gradle.api.internal.initialization.transform.utils.InstrumentationTransformUtils.FILE_MISSING_HASH;
import static org.gradle.api.internal.initialization.transform.utils.InstrumentationTransformUtils.FILE_NAME_PROPERTY_NAME;
import static org.gradle.api.internal.initialization.transform.utils.InstrumentationTransformUtils.METADATA_FILE_NAME;
import static org.gradle.api.internal.initialization.transform.utils.InstrumentationTransformUtils.SUPER_TYPES_FILE_NAME;
import static org.gradle.api.internal.initialization.transform.utils.InstrumentationTransformUtils.createInstrumentationClasspathMarker;
import static org.gradle.internal.classpath.transforms.MrJarUtils.isInUnsupportedMrJarVersionedDirectory;

/**
 * TODO: This class has similar implementation in build-logic/packaging/src/main/kotlin/gradlebuild/instrumentation/transforms/CollectDirectClassSuperTypesTransform.kt.
 *  We could reuse the same class at some point.
 */
@DisableCachingByDefault(because = "Not worth caching.")
public abstract class CollectDirectClassSuperTypesTransform implements TransformAction<CollectDirectClassSuperTypesTransform.Parameters> {

    public interface Parameters extends TransformParameters {
        @Internal
        Property<CacheInstrumentationTypeRegistryBuildService> getBuildService();
    }
    private static final Predicate<String> ACCEPTED_TYPES = type -> type != null && !type.startsWith("java/lang");

    @Inject
    protected abstract ObjectFactory getObjects();

    @PathSensitive(PathSensitivity.NAME_ONLY)
    @InputArtifact
    public abstract Provider<FileSystemLocation> getInput();

    @Override
    public void transform(TransformOutputs outputs) {
        File artifact = getInput().get().getAsFile();
        if (!artifact.exists()) {
            // Files can be passed to the artifact transform even if they don't exist,
            // in the case when user adds a file classpath via files("path/to/jar").
            // Unfortunately we don't filter them out before the artifact transform is run.
            writeOutput(artifact, outputs, Collections.emptyMap(), Collections.emptySet());
            return;
        }

        try {
            Map<String, Set<String>> superTypes = new TreeMap<>();
            Set<String> dependencies = new TreeSet<>();
            analyzeArtifact(artifact, superTypes, dependencies);
            writeOutput(artifact, outputs, superTypes, dependencies);
        } catch (IOException | FileException ignored) {
            // We support badly formatted jars on the build classpath
            // see: https://github.com/gradle/gradle/issues/13816
            writeOutput(artifact, outputs, Collections.emptyMap(), Collections.emptySet());
        }
    }

    private void analyzeArtifact(File artifact, Map<String, Set<String>> superTypesCollector, Set<String> dependenciesCollector) throws IOException {
        // We cannot inject internal services in to the transform directly, but we can create them via object factory
        InjectedInstrumentationServices services = getObjects().newInstance(InjectedInstrumentationServices.class);
        ClasspathWalker walker = services.getClasspathWalker();
        walker.visit(artifact, entry -> {
            if (entry.getName().endsWith(".class") && isInUnsupportedMrJarVersionedDirectory(entry)) {
                ClassReader reader = new ClassReader(entry.getContent());
                String className = reader.getClassName();
                Set<String> classSuperTypes = getSuperTypes(reader);
                collectArtifactClassDependencies(reader, dependenciesCollector);
                if (!classSuperTypes.isEmpty()) {
                    superTypesCollector.put(className, classSuperTypes);
                }
            }
        });
    }

    private static Set<String> getSuperTypes(ClassReader reader) {
        return Stream.concat(Stream.of(reader.getSuperName()), Stream.of(reader.getInterfaces()))
            .filter(ACCEPTED_TYPES)
            .collect(toImmutableSortedSet(Ordering.natural()));
    }

    private static void collectArtifactClassDependencies(ClassReader reader, Set<String> collector) {
        ClassAnalysisUtils.getClassDependencies(reader, dependencyDescriptor -> {
            if (ACCEPTED_TYPES.test(dependencyDescriptor)) {
                collector.add(dependencyDescriptor);
            }
        });
    }

    private void writeOutput(File artifact, TransformOutputs outputs, Map<String, Set<String>> superTypes, Set<String> dependencies) {
        File outputDir = outputs.dir("analysis");
        File metadata = new File(outputDir, METADATA_FILE_NAME);
        writeMetadata(artifact, metadata);
        File superTypesFile = new File(outputDir, SUPER_TYPES_FILE_NAME);
        writeSuperTypes(superTypes, superTypesFile);
        File dependenciesFile = new File(outputDir, DEPENDENCIES_FILE_NAME);
        writeDependencies(dependencies, dependenciesFile);
        createInstrumentationClasspathMarker(outputs);
    }

    private void writeMetadata(File artifact, File metadata) {
        CacheInstrumentationTypeRegistryBuildService buildService = getParameters().getBuildService().get();
        writeOutput(metadata, writer -> {
            String hash = firstNonNull(buildService.getArtifactHash(artifact), FILE_MISSING_HASH);
            writer.write(FILE_NAME_PROPERTY_NAME + "=" + artifact.getName() + "\n");
            writer.write(FILE_HASH_PROPERTY_NAME + "=" + hash + "\n");
        });
    }

    private static void writeSuperTypes(Map<String, Set<String>> superTypes, File metadata) {
        writeOutput(metadata, writer -> {
            for (Map.Entry<String, Set<String>> entry : superTypes.entrySet()) {
                writer.write(entry.getKey() + "=" + String.join(",", entry.getValue()) + "\n");
            }
        });
    }

    private static void writeDependencies(Set<String> dependencies, File metadata) {
        writeOutput(metadata, writer -> {
            for (String dependency : dependencies) {
                writer.write(dependency + "\n");
            }
        });
    }

    private static void writeOutput(File outputFile, IoConsumer<Writer> writerConsumer) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
            writerConsumer.accept(writer);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
