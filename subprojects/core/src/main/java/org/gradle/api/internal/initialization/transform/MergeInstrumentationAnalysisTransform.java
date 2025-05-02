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
import org.gradle.api.internal.initialization.transform.services.CacheInstrumentationDataBuildService;
import org.gradle.api.internal.initialization.transform.utils.InstrumentationAnalysisSerializer;
import org.gradle.api.internal.initialization.transform.utils.InstrumentationTransformUtils.InstrumentationInputType;
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
import java.io.File;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.gradle.api.internal.initialization.transform.utils.InstrumentationTransformUtils.DEPENDENCY_ANALYSIS_FILE_NAME;
import static org.gradle.api.internal.initialization.transform.utils.InstrumentationTransformUtils.MERGE_OUTPUT_DIR;
import static org.gradle.api.internal.initialization.transform.utils.InstrumentationTransformUtils.createInstrumentationClasspathMarker;
import static org.gradle.api.internal.initialization.transform.utils.InstrumentationTransformUtils.getInputType;
import static org.gradle.api.internal.initialization.transform.utils.InstrumentationTransformUtils.outputOriginalArtifact;

/**
 * A transform that merges all instrumentation related metadata for a single artifact.<br><br>
 *
 * Outputs either a directory with analysis files OR original artifact.<br><br>
 *
 * Directory with analysis files has next content:<br>
 * 1. Instrumentation classpath marker file.<br>
 * 2. A properties file with original file hash and original file name.<br>
 * 3. A properties file with instrumented class dependencies in a file.<br><br>
 *
 * File with instrumented class dependencies is a properties file like:<br>
 * [class name 1]=[instrumented super type 1],[instrumented super type 2],...<br>
 * [class name 2]=[instrumented super type 1],[instrumented super type 2],...<br>
 * ...
 */
@DisableCachingByDefault(because = "Not worth caching.")
public abstract class MergeInstrumentationAnalysisTransform implements TransformAction<MergeInstrumentationAnalysisTransform.Parameters> {

    public interface Parameters extends TransformParameters {
        @Internal
        Property<CacheInstrumentationDataBuildService> getBuildService();
        @Internal
        Property<Long> getContextId();

        /**
         * Analysis result is an input, but we access it through build service.
         */
        @InputFiles
        @PathSensitive(PathSensitivity.NAME_ONLY)
        ConfigurableFileCollection getTypeHierarchyAnalysis();
    }

    @Inject
    public abstract ObjectFactory getObjects();

    @PathSensitive(PathSensitivity.NAME_ONLY)
    @InputArtifact
    public abstract Provider<FileSystemLocation> getInput();

    @Override
    public void transform(TransformOutputs outputs) {
        // We simulate fan-in behaviour:
        // We expect that a transform before this one outputs three artifacts: 1. analysis metadata, 2. the original file and 3. instrumentation marker file.
        // So if the input is analysis metadata we merge it and output it, otherwise it's original artifact, and we output that.
        File input = getInput().get().getAsFile();
        InstrumentationInputType inputType = getInputType(input);
        switch (inputType) {
            case DEPENDENCY_ANALYSIS_DATA:
                doMergeAndOutputAnalysis(input, outputs);
                return;
            case ORIGINAL_ARTIFACT:
                outputOriginalArtifact(outputs, input);
                return;
            case INSTRUMENTATION_MARKER:
            case TYPE_HIERARCHY_ANALYSIS_DATA:
                // We don't need to do anything with the marker file and type hierarchy
                return;
            default:
                throw new IllegalStateException("Unexpected input type: " + inputType);
        }
    }

    private void doMergeAndOutputAnalysis(File input, TransformOutputs outputs) {
        InstrumentationAnalysisSerializer serializer = getParameters().getBuildService().get().getCachedInstrumentationAnalysisSerializer();
        InstrumentationTypeRegistry registry = getInstrumentationTypeRegistry();

        InstrumentationDependencyAnalysis data = serializer.readDependencyAnalysis(input);
        Map<String, Set<String>> dependenciesSuperTypes = new TreeMap<>();
        for (String className : data.getDependencies().keySet()) {
            Set<String> superTypes = registry.getSuperTypes(className);
            if (!superTypes.isEmpty()) {
                dependenciesSuperTypes.put(className, new TreeSet<>(superTypes));
            }
        }

        createInstrumentationClasspathMarker(outputs);
        File output = outputs.file(MERGE_OUTPUT_DIR + "/" + DEPENDENCY_ANALYSIS_FILE_NAME);
        serializer.writeDependencyAnalysis(output, new InstrumentationDependencyAnalysis(data.getMetadata(), dependenciesSuperTypes));
    }

    private InstrumentationTypeRegistry getInstrumentationTypeRegistry() {
        long contextId = getParameters().getContextId().get();
        CacheInstrumentationDataBuildService buildService = getParameters().getBuildService().get();
        return buildService.getInstrumentationTypeRegistry(contextId);
    }
}
