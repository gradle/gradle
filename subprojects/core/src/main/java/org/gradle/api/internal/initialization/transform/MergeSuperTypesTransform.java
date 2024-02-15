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
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.gradle.api.internal.initialization.transform.utils.InstrumentationTransformUtils.FILE_HASH_PROPERTY_NAME;
import static org.gradle.api.internal.initialization.transform.utils.InstrumentationTransformUtils.createInstrumentationClasspathMarker;
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
        File output = outputs.file("merged/" + input.getName());
        InjectedInstrumentationServices services = getObjects().newInstance(InjectedInstrumentationServices.class);
        try (InputStream inputStream = Files.newInputStream(input.toPath());
             BufferedWriter outputWriter = new BufferedWriter(new FileWriter(output))
        ) {
            Properties properties = loadProperties(inputStream);
            outputWriter.write(FILE_HASH_PROPERTY_NAME + "=" + properties.getProperty(FILE_HASH_PROPERTY_NAME) + "\n");
            writeSuperTypes(outputWriter, services, properties);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void writeSuperTypes(BufferedWriter outputWriter, InjectedInstrumentationServices services, Properties properties) throws IOException {
        InstrumentationTypeRegistry registry = getInstrumentationTypeRegistry(services);
        for (String className : getSortedClassNames(properties)) {
            Set<String> superTypes = registry.getSuperTypes(className);
            if (!superTypes.isEmpty()) {
                outputWriter.write(className + "=" + superTypes.stream().sorted().collect(Collectors.joining(",")) + "\n");
            }
        }
    }

    private InstrumentationTypeRegistry getInstrumentationTypeRegistry(InjectedInstrumentationServices internalServices) {
        CacheInstrumentationTypeRegistryBuildService buildService = getParameters().getBuildService().get();
        return buildService.getInstrumentingTypeRegistry(internalServices.getGradleCoreInstrumentingTypeRegistry());
    }

    private static Properties loadProperties(InputStream inputStream) throws IOException {
        Properties properties = new Properties();
        properties.load(inputStream);
        return properties;
    }

    private static Set<String> getSortedClassNames(Properties properties) {
        Set<String> types = new TreeSet<>(properties.stringPropertyNames());
        types.remove(FILE_HASH_PROPERTY_NAME);
        return types;
    }
}
