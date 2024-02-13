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

import org.gradle.api.artifacts.transform.InputArtifact;
import org.gradle.api.artifacts.transform.TransformAction;
import org.gradle.api.artifacts.transform.TransformOutputs;
import org.gradle.api.artifacts.transform.TransformParameters;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.cache.GlobalCacheLocations;
import org.gradle.internal.classpath.ClasspathWalker;
import org.gradle.internal.classpath.InPlaceClasspathBuilder;
import org.gradle.internal.classpath.transforms.ClasspathElementTransform;
import org.gradle.internal.classpath.transforms.ClasspathElementTransformFactory;
import org.gradle.internal.classpath.transforms.ClasspathElementTransformFactoryForAgent;
import org.gradle.internal.classpath.transforms.ClasspathElementTransformFactoryForLegacy;
import org.gradle.internal.classpath.transforms.InstrumentingClassTransform;
import org.gradle.internal.classpath.types.GradleCoreInstrumentationTypeRegistry;
import org.gradle.internal.classpath.types.InstrumentationTypeRegistry;
import org.gradle.internal.file.Stat;
import org.gradle.internal.instrumentation.api.types.BytecodeInterceptorFilter;
import org.gradle.internal.vfs.FileSystemAccess;
import org.gradle.util.internal.GFileUtils;
import org.gradle.work.DisableCachingByDefault;

import javax.inject.Inject;
import java.io.File;

import static org.gradle.api.internal.initialization.transform.BaseInstrumentingArtifactTransform.InstrumentArtifactTransformParameters;
import static org.gradle.api.internal.initialization.transform.utils.InstrumentationTransformUtils.createNewFile;
import static org.gradle.internal.classpath.TransformedClassPath.INSTRUMENTED_DIR_NAME;
import static org.gradle.internal.classpath.TransformedClassPath.INSTRUMENTED_MARKER_FILE_NAME;
import static org.gradle.internal.classpath.TransformedClassPath.ORIGINAL_DIR_NAME;
import static org.gradle.internal.classpath.TransformedClassPath.ORIGINAL_JAR_HASH_EXTENSION;

/**
 * Base artifact transform that instruments plugins with Gradle instrumentation, e.g. for configuration cache detection or property upgrades.
 */
@DisableCachingByDefault(because = "Instrumented jars are too big to cache")
public abstract class BaseInstrumentingArtifactTransform implements TransformAction<InstrumentArtifactTransformParameters> {

    public interface InstrumentArtifactTransformParameters extends TransformParameters {
        @Internal
        Property<CacheInstrumentationTypeRegistryBuildService> getBuildService();
        @Input
        Property<Boolean> getAgentSupported();
    }

    @Inject
    public abstract ObjectFactory getObjects();

    @PathSensitive(PathSensitivity.NAME_ONLY)
    @InputArtifact
    public abstract Provider<FileSystemLocation> getInput();

    protected abstract BytecodeInterceptorFilter provideInterceptorFilter();

    protected abstract File inputArtifact();

    @Override
    public void transform(TransformOutputs outputs) {
        File input = inputArtifact();
        if (!input.exists()) {
            // Files can be passed to the artifact transform even if they don't exist,
            // in the case when user adds a file classpath via files("path/to/jar").
            // Unfortunately we don't filter them out before the artifact transform is run.
            return;
        }

        // A marker file that indicates that the result is instrumented jar,
        // this is important so TransformedClassPath can correctly filter instrumented jars.
        createNewFile(outputs.file(INSTRUMENTED_MARKER_FILE_NAME));

        // Instrument jars
        InjectedInstrumentationServices injectedServices = getObjects().newInstance(InjectedInstrumentationServices.class);
        doTransform(input, outputs, injectedServices);

        // Link to original jars if they are safe to load from cache loader otherwise copy a jar
        if (input.isDirectory() || injectedServices.getGlobalCacheLocations().isInsideGlobalCache(input.getAbsolutePath())) {
            // Here we just create a file with the hash of the original jar, so that we can reconstruct an original jar classpath.
            // Directories are ok to use outside the cache, since they are not locked by the daemon.
            // Jars that are already in the global cache don't need to be copied, since
            // the global caches are additive only and jars shouldn't be deleted or changed during the build.
            String hash = injectedServices.fileSystemAccess.read(input.getAbsolutePath()).getHash().toString();
            createNewFile(outputs.file(ORIGINAL_DIR_NAME + "/" + hash + ORIGINAL_JAR_HASH_EXTENSION));
        } else {
            // Jars that are in some mutable location (e.g. build/ directory) need to be copied to the global cache,
            // since daemon keeps them locked when loading them to a classloader, which prevents e.g. deleting the build directory on windows
            File copyOfOriginalFile = outputs.file(ORIGINAL_DIR_NAME + "/" + input.getName());
            GFileUtils.copyFile(input, copyOfOriginalFile);
        }
    }

    private void doTransform(File input, TransformOutputs outputs, InjectedInstrumentationServices injectedServices) {
        String outputPath = getOutputPath(input);
        File output = input.isDirectory() ? outputs.dir(outputPath) : outputs.file(outputPath);
        InstrumentationTypeRegistry typeRegistry = getParameters().getBuildService().isPresent()
            ? getParameters().getBuildService().get().getInstrumentingTypeRegistry(injectedServices.getGradleCoreInstrumentingTypeRegistry())
            : InstrumentationTypeRegistry.empty();
        ClasspathElementTransformFactory transformFactory = injectedServices.getTransformFactory(isAgentSupported());
        ClasspathElementTransform transform = transformFactory.createTransformer(input, new InstrumentingClassTransform(provideInterceptorFilter()), typeRegistry);
        transform.transform(output);
    }

    private static String getOutputPath(File input) {
        return INSTRUMENTED_DIR_NAME + "/" + input.getName();
    }

    private boolean isAgentSupported() {
        return getParameters().getAgentSupported().get();
    }

    static class InjectedInstrumentationServices {

        private final ClasspathElementTransformFactoryForAgent transformFactory;
        private final ClasspathElementTransformFactoryForLegacy legacyTransformFactory;
        private final GlobalCacheLocations globalCacheLocations;
        private final GradleCoreInstrumentationTypeRegistry gradleCoreInstrumentingTypeRegistry;
        private final FileSystemAccess fileSystemAccess;

        @Inject
        public InjectedInstrumentationServices(
            Stat stat,
            GlobalCacheLocations globalCacheLocations,
            GradleCoreInstrumentationTypeRegistry gradleCoreInstrumentingTypeRegistry,
            FileSystemAccess fileSystemAccess
        ) {
            this.transformFactory = new ClasspathElementTransformFactoryForAgent(new InPlaceClasspathBuilder(), new ClasspathWalker(stat));
            this.legacyTransformFactory = new ClasspathElementTransformFactoryForLegacy(new InPlaceClasspathBuilder(), new ClasspathWalker(stat));
            this.globalCacheLocations = globalCacheLocations;
            this.gradleCoreInstrumentingTypeRegistry = gradleCoreInstrumentingTypeRegistry;
            this.fileSystemAccess = fileSystemAccess;
        }

        public ClasspathElementTransformFactory getTransformFactory(boolean isAgentSupported) {
            return isAgentSupported ? transformFactory : legacyTransformFactory;
        }

        public GlobalCacheLocations getGlobalCacheLocations() {
            return globalCacheLocations;
        }

        public GradleCoreInstrumentationTypeRegistry getGradleCoreInstrumentingTypeRegistry() {
            return gradleCoreInstrumentingTypeRegistry;
        }
    }
}
