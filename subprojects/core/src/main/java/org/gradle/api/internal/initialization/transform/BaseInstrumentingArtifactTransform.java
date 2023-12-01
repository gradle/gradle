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
import org.gradle.api.tasks.Optional;
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
import org.gradle.internal.classpath.types.InstrumentingTypeRegistry;
import org.gradle.internal.file.Stat;
import org.gradle.util.internal.GFileUtils;
import org.gradle.work.DisableCachingByDefault;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;

import static org.gradle.api.internal.initialization.transform.BaseInstrumentingArtifactTransform.InstrumentArtifactTransformParameters;
import static org.gradle.internal.classpath.TransformedClassPath.INSTRUMENTED_JAR_DIR_NAME;
import static org.gradle.internal.classpath.TransformedClassPath.INSTRUMENTED_MARKER_FILE_NAME;
import static org.gradle.internal.classpath.TransformedClassPath.ORIGINAL_JAR_DIR_NAME;

/**
 * Base artifact transform that instruments plugins with Gradle instrumentation, e.g. for configuration cache detection or property upgrades.
 */
@DisableCachingByDefault(because = "Instrumented jars are too big to cache")
public abstract class BaseInstrumentingArtifactTransform implements TransformAction<InstrumentArtifactTransformParameters> {

    public interface InstrumentArtifactTransformParameters extends TransformParameters {
        @Input
        Property<Boolean> getAgentSupported();

        @Input
        Property<Integer> getMaxSupportedJavaVersion();

        @Input
        @Optional
        Property<String> getUpgradedPropertiesHash();
    }

    @Inject
    public abstract ObjectFactory getObjects();

    @PathSensitive(PathSensitivity.NAME_ONLY)
    @InputArtifact
    public abstract Provider<FileSystemLocation> getInput();

    @Override
    public void transform(TransformOutputs outputs) {
        File input = getInput().get().getAsFile();
        if (!input.exists()) {
            // Don't instrument files that don't exist, these could be files added to classpath via files()
            return;
        }

        // A marker file that indicates that the result is instrumented jar,
        // this is important so TransformedClassPath can correctly filter instrumented jars.
        createNewFile(outputs.file(INSTRUMENTED_MARKER_FILE_NAME));

        // Instrument jars
        InstrumentationServices instrumentationServices = getObjects().newInstance(InstrumentationServices.class);
        File outputFile = outputs.file(INSTRUMENTED_JAR_DIR_NAME + "/" + input.getName());
        ClasspathElementTransformFactory transformFactory = instrumentationServices.getTransformFactory(getParameters().getAgentSupported().get());
        ClasspathElementTransform transform = transformFactory.createTransformer(input, new InstrumentingClassTransform(), InstrumentingTypeRegistry.EMPTY);
        transform.transform(outputFile);

        // Copy original jars after in case they are not in global cache
        if (instrumentationServices.getGlobalCacheLocations().isInsideGlobalCache(input.getAbsolutePath())) {
            // The global caches are additive only, so we can use it directly since it shouldn't be deleted or changed during the build.
            outputs.file(getInput());
        } else {
            File copyOfOriginalFile = outputs.file(ORIGINAL_JAR_DIR_NAME + "/" + input.getName());
            GFileUtils.copyFile(input, copyOfOriginalFile);
        }
    }

    private boolean createNewFile(File file) {
        try {
            return file.createNewFile();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }


    static class InstrumentationServices {

        private final ClasspathElementTransformFactoryForAgent transformFactory;
        private final ClasspathElementTransformFactoryForLegacy legacyTransformFactory;
        private final GlobalCacheLocations globalCacheLocations;

        @Inject
        public InstrumentationServices(Stat stat, GlobalCacheLocations globalCacheLocations) {
            this.transformFactory = new ClasspathElementTransformFactoryForAgent(new InPlaceClasspathBuilder(), new ClasspathWalker(stat));
            this.legacyTransformFactory = new ClasspathElementTransformFactoryForLegacy(new InPlaceClasspathBuilder(), new ClasspathWalker(stat));
            this.globalCacheLocations = globalCacheLocations;
        }

        public ClasspathElementTransformFactory getTransformFactory(boolean isAgentSupported) {
            return isAgentSupported ? transformFactory : legacyTransformFactory;
        }

        public GlobalCacheLocations getGlobalCacheLocations() {
            return globalCacheLocations;
        }
    }
}
