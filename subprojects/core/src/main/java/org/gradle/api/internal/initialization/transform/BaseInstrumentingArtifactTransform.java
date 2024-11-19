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

import com.google.common.base.Function;
import org.gradle.api.artifacts.transform.InputArtifact;
import org.gradle.api.artifacts.transform.TransformAction;
import org.gradle.api.artifacts.transform.TransformOutputs;
import org.gradle.api.artifacts.transform.TransformParameters;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.internal.initialization.transform.services.CacheInstrumentationDataBuildService;
import org.gradle.api.internal.initialization.transform.services.InjectedInstrumentationServices;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.services.ServiceReference;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.internal.classpath.transforms.ClasspathElementTransform;
import org.gradle.internal.classpath.transforms.ClasspathElementTransformFactory;
import org.gradle.internal.classpath.transforms.InstrumentingClassTransform;
import org.gradle.internal.lazy.Lazy;
import org.gradle.util.internal.GFileUtils;
import org.gradle.work.DisableCachingByDefault;

import javax.inject.Inject;
import java.io.File;

import static org.gradle.api.internal.initialization.transform.BaseInstrumentingArtifactTransform.Parameters;
import static org.gradle.api.internal.initialization.transform.utils.InstrumentationTransformUtils.createInstrumentationClasspathMarker;
import static org.gradle.api.internal.initialization.transform.utils.InstrumentationTransformUtils.createNewFile;
import static org.gradle.internal.classpath.TransformedClassPath.FileMarker.AGENT_INSTRUMENTATION_MARKER;
import static org.gradle.internal.classpath.TransformedClassPath.FileMarker.LEGACY_INSTRUMENTATION_MARKER;
import static org.gradle.internal.classpath.TransformedClassPath.FileMarker.ORIGINAL_FILE_DOES_NOT_EXIST_MARKER;
import static org.gradle.internal.classpath.TransformedClassPath.INSTRUMENTED_DIR_NAME;
import static org.gradle.internal.classpath.TransformedClassPath.INSTRUMENTED_ENTRY_PREFIX;
import static org.gradle.internal.classpath.TransformedClassPath.ORIGINAL_DIR_NAME;

/**
 * Base artifact transform that instruments plugins with Gradle instrumentation, e.g. for configuration cache detection or property upgrades.
 */
@DisableCachingByDefault(because = "Instrumented jars are too big to cache")
public abstract class BaseInstrumentingArtifactTransform<T extends Parameters> implements TransformAction<T> {

    public interface Parameters extends TransformParameters {
        @ServiceReference
        Property<CacheInstrumentationDataBuildService> getBuildService();
        @Internal
        Property<Long> getContextId();
        @Input
        Property<Boolean> getAgentSupported();
    }

    protected final Lazy<InjectedInstrumentationServices> internalServices = Lazy.unsafe().of(() -> getObjects().newInstance(InjectedInstrumentationServices.class));

    @Inject
    public abstract ObjectFactory getObjects();

    @PathSensitive(PathSensitivity.NAME_ONLY)
    @InputArtifact
    public abstract Provider<FileSystemLocation> getInput();

    protected void doTransform(File artifactToTransform, TransformOutputs outputs) {
        createInstrumentationClasspathMarker(outputs);
        if (!artifactToTransform.exists()) {
            createNewFile(outputs.file(ORIGINAL_FILE_DOES_NOT_EXIST_MARKER.getFileName()));
            return;
        }

        if (isAgentSupported()) {
            // When agent is supported, we output an instrumented jar and an original jar,
            // so we can then later reconstruct instrumented jars classpath and original jars classpath.
            // We add `instrumented-` prefix to the file since names for the same transform needs to be unique when querying results via ArtifactCollection.
            createNewFile(outputs.file(AGENT_INSTRUMENTATION_MARKER.getFileName()));
            doTransform(artifactToTransform, outputs, originalName -> INSTRUMENTED_ENTRY_PREFIX + originalName);
        } else {
            createNewFile(outputs.file(LEGACY_INSTRUMENTATION_MARKER.getFileName()));
            doTransform(artifactToTransform, outputs, originalName -> originalName);
        }
    }

    private boolean isAgentSupported() {
        return getParameters().getAgentSupported().get();
    }

    private void doTransform(File input, TransformOutputs outputs, Function<String, String> instrumentedEntryNameMapper) {
        String outputPath = getOutputPath(input, instrumentedEntryNameMapper);
        File output = input.isDirectory() ? outputs.dir(outputPath) : outputs.file(outputPath);
        try (InstrumentingClassTransformProvider provider = instrumentingClassTransformProvider(outputs)) {
            InstrumentingClassTransform classTransform = provider.getClassTransform();
            ClasspathElementTransformFactory transformFactory = internalServices.get().getTransformFactory(isAgentSupported());
            ClasspathElementTransform transform = transformFactory.createTransformer(input, classTransform);
            transform.transform(output);
        }
    }

    private static String getOutputPath(File input, Function<String, String> instrumentedEntryNameMapper) {
        return INSTRUMENTED_DIR_NAME + "/" + instrumentedEntryNameMapper.apply(input.getName());
    }

    protected void doOutputOriginalArtifact(File input, TransformOutputs outputs) {
        createInstrumentationClasspathMarker(outputs);
        // Output original file if it's safe to load from cache loader ELSE copy an entry
        if (input.isDirectory()) {
            // Directories are ok to use outside the cache, since they are not locked by the daemon.
            outputs.dir(input);
        } else if (internalServices.get().getGlobalCacheLocations().isInsideGlobalCache(input.getAbsolutePath())) {
            // Jars that are already in the global cache don't need to be copied, since
            // the global caches are additive only and jars shouldn't be deleted or changed during the build.
            outputs.file(input);
        } else {
            // Jars that are in some mutable location (e.g. build/ directory) need to be copied to the global cache,
            // since daemon keeps them locked when loading them to a classloader, which prevents e.g. deleting the build directory on windows
            File copyOfOriginalFile = outputs.file(ORIGINAL_DIR_NAME + "/" + input.getName());
            GFileUtils.copyFile(input, copyOfOriginalFile);
        }
    }

    protected abstract InstrumentingClassTransformProvider instrumentingClassTransformProvider(TransformOutputs outputs);

    protected interface InstrumentingClassTransformProvider extends AutoCloseable {
        InstrumentingClassTransform getClassTransform();

        @Override
        void close();
    }
}
