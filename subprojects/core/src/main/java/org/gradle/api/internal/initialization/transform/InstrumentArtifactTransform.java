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
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.cache.GlobalCache;
import org.gradle.cache.GlobalCacheLocations;
import org.gradle.cache.internal.DefaultGlobalCacheLocations;
import org.gradle.internal.classpath.ClasspathWalker;
import org.gradle.internal.classpath.InPlaceClasspathBuilder;
import org.gradle.internal.classpath.TransformedClassPath;
import org.gradle.internal.classpath.transforms.ClasspathElementTransform;
import org.gradle.internal.classpath.transforms.ClasspathElementTransformFactoryForAgent;
import org.gradle.internal.classpath.transforms.InstrumentingClassTransform;
import org.gradle.internal.classpath.types.InstrumentingTypeRegistry;
import org.gradle.internal.file.Stat;
import org.gradle.util.internal.GFileUtils;
import org.gradle.work.DisableCachingByDefault;

import javax.inject.Inject;
import java.io.File;
import java.util.List;

import static org.gradle.api.internal.initialization.transform.InstrumentArtifactTransform.InstrumentArtifactTransformParameters;

@DisableCachingByDefault(because = "Not enable yet, since original instrumentation is also not cached in build cache.")
public abstract class InstrumentArtifactTransform implements TransformAction<InstrumentArtifactTransformParameters> {

    public interface InstrumentArtifactTransformParameters extends TransformParameters {
        @InputFiles
        @PathSensitive(PathSensitivity.NAME_ONLY)
        ConfigurableFileCollection getClassHierarchy();

        @Internal
        ListProperty<GlobalCache> getCacheLocations();
    }

    @Inject
    public abstract ObjectFactory getObjects();

    @InputArtifact
    @Classpath
    public abstract Provider<FileSystemLocation> getInput();

    private File getInputAsFile() {
        return getInput().get().getAsFile();
    }

    @Override
    public void transform(TransformOutputs outputs) {
        if (!getInputAsFile().exists()) {
            // Don't instrument files that don't exist, these could be files added to classpath via files()
            return;
        }

        // TransformedClassPath.handleInstrumentingArtifactTransform depends on the order and this naming, we should make it more resilient in the future
        String instrumentedJarName = getInput().get().getAsFile().getName().replaceFirst("\\.jar$", TransformedClassPath.INSTRUMENTED_JAR_EXTENSION);
        InstrumentationServices instrumentationServices = getObjects().newInstance(InstrumentationServices.class);
        File outputFile = outputs.file(instrumentedJarName);
        ClasspathElementTransformFactoryForAgent transformFactory = instrumentationServices.getTransformFactory();
        ClasspathElementTransform transform = transformFactory.createTransformer(getInputAsFile(), new InstrumentingClassTransform(), InstrumentingTypeRegistry.EMPTY);
        transform.transform(outputFile);

        List<GlobalCache> globalCaches = getParameters().getCacheLocations().get();
        GlobalCacheLocations globalCacheRoots = new DefaultGlobalCacheLocations(globalCaches);
        if (globalCacheRoots.isInsideGlobalCache(getInputAsFile().getAbsolutePath())) {
            // The global caches are additive only, so we can use it directly since it shouldn't be deleted or changed during the build.
            outputs.file(getInput());
        } else {
            File copyOfOriginalFile = outputs.file(getInputAsFile().getName());
            GFileUtils.copyFile(getInputAsFile(), copyOfOriginalFile);
        }
    }

    static class InstrumentationServices {

        private final ClasspathElementTransformFactoryForAgent transformFactory;

        @Inject
        public InstrumentationServices(Stat stat) {
            this.transformFactory = new ClasspathElementTransformFactoryForAgent(new InPlaceClasspathBuilder(), new ClasspathWalker(stat));
        }

        public ClasspathElementTransformFactoryForAgent getTransformFactory() {
            return transformFactory;
        }
    }
}
