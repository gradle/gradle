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

import org.gradle.api.artifacts.transform.InputArtifact;
import org.gradle.api.artifacts.transform.TransformAction;
import org.gradle.api.artifacts.transform.TransformOutputs;
import org.gradle.api.artifacts.transform.TransformParameters;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.internal.initialization.transform.services.CacheInstrumentationTypeRegistryBuildService;
import org.gradle.api.internal.initialization.transform.services.InjectedInstrumentationServices;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.internal.classpath.types.InstrumentationTypeRegistry;
import org.gradle.work.DisableCachingByDefault;

import javax.inject.Inject;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.file.Files;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.gradle.api.internal.initialization.transform.utils.InstrumentationTransformUtils.DEPENDENCIES_FILE_NAME;
import static org.gradle.api.internal.initialization.transform.utils.InstrumentationTransformUtils.DEPENDENCIES_SUPER_TYPES_FILE_NAME;
import static org.gradle.api.internal.initialization.transform.utils.InstrumentationTransformUtils.MERGE_OUTPUT_DIR;
import static org.gradle.api.internal.initialization.transform.utils.InstrumentationTransformUtils.METADATA_FILE_NAME;
import static org.gradle.api.internal.initialization.transform.utils.InstrumentationTransformUtils.createInstrumentationClasspathMarker;
import static org.gradle.api.internal.initialization.transform.utils.InstrumentationTransformUtils.newBufferedUtf8Writer;
import static org.gradle.internal.classpath.TransformedClassPath.INSTRUMENTATION_CLASSPATH_MARKER_FILE_NAME;

/**
 * A transform that merges all instrumentation related super types for classes from a single artifact to a single file.
 * It uses hash of the original file as an id to link to the original artifact.<br><br>
 *
 * Output file looks like:<br>
 * -hash-=[hash]<br>
 * [class name 1]=[super type 1],[super type 2],...<br>
 * [class name 2]=[super type 1],[super type 2],...<br>
 * ...
 */
@DisableCachingByDefault(because = "Not worth caching.")
public abstract class MergeSuperTypesTransform implements TransformAction<MergeSuperTypesTransform.InstrumentArtifactTransformParameters> {

    public interface InstrumentArtifactTransformParameters extends TransformParameters {
        @Internal
        Property<CacheInstrumentationTypeRegistryBuildService> getBuildService();

        /**
         * Original classpath is an input, since if original classpath changes that means
         * that also type hierarchy could have changed, so we need to re-merge the hierarchy.
         */
        @InputFiles
        @PathSensitive(PathSensitivity.NAME_ONLY)
        ConfigurableFileCollection getOriginalClasspath();
    }

    @Inject
    public abstract ObjectFactory getObjects();

    @PathSensitive(PathSensitivity.NAME_ONLY)
    @InputArtifact
    public abstract Provider<FileSystemLocation> getInput();

    @Override
    public void transform(TransformOutputs outputs) {
        File input = getInput().get().getAsFile();
        if (input.getName().equals(INSTRUMENTATION_CLASSPATH_MARKER_FILE_NAME)) {
            return;
        }

        createInstrumentationClasspathMarker(outputs);
        File outputDir = outputs.dir(MERGE_OUTPUT_DIR);
        File dependenciesSuperTypes = new File(outputDir, DEPENDENCIES_SUPER_TYPES_FILE_NAME);
        InjectedInstrumentationServices services = getObjects().newInstance(InjectedInstrumentationServices.class);
        try (BufferedWriter writer = newBufferedUtf8Writer(dependenciesSuperTypes)) {
            File dependencies = new File(input, DEPENDENCIES_FILE_NAME);
            writeDependenciesSuperTypes(dependencies, writer, services);
            Files.copy(new File(input, METADATA_FILE_NAME).toPath(), new File(outputDir, METADATA_FILE_NAME).toPath());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void writeDependenciesSuperTypes(File dependencies, Writer writer, InjectedInstrumentationServices services) throws IOException {
        InstrumentationTypeRegistry registry = getInstrumentationTypeRegistry(services);
        try (Stream<String> stream = Files.lines(dependencies.toPath())) {
            stream.forEach(className -> {
                Set<String> superTypes = registry.getSuperTypes(className);
                if (!superTypes.isEmpty()) {
                    writeDependencySuperTypes(className, superTypes, writer);
                }
            });
        }
    }

    private static void writeDependencySuperTypes(String className, Set<String> superTypes, Writer writer) {
        try {
            writer.write(className + "=" + superTypes.stream().sorted().collect(Collectors.joining(",")) + "\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private InstrumentationTypeRegistry getInstrumentationTypeRegistry(InjectedInstrumentationServices internalServices) {
        CacheInstrumentationTypeRegistryBuildService buildService = getParameters().getBuildService().get();
        return buildService.getInstrumentingTypeRegistry(internalServices.getGradleCoreInstrumentingTypeRegistry());
    }
}
