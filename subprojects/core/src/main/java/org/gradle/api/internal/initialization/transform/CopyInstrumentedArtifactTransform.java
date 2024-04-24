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
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.internal.initialization.transform.services.CacheInstrumentationDataBuildService;
import org.gradle.api.internal.initialization.transform.services.InjectedInstrumentationServices;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Internal;
import org.gradle.util.internal.GFileUtils;
import org.gradle.work.DisableCachingByDefault;

import javax.inject.Inject;
import java.io.File;
import java.util.function.Supplier;

import static org.gradle.api.internal.initialization.transform.utils.InstrumentationTransformUtils.createInstrumentationClasspathMarker;
import static org.gradle.internal.classpath.TransformedClassPath.ORIGINAL_DIR_NAME;

@DisableCachingByDefault(because = "Not worth caching.")
public abstract class CopyInstrumentedArtifactTransform implements TransformAction<CopyInstrumentedArtifactTransform.Parameters> {

    public interface Parameters extends TransformParameters {
        @Internal
        Property<CacheInstrumentationDataBuildService> getBuildService();
    }

    protected final Supplier<InjectedInstrumentationServices> internalServices = () -> getParameters().getBuildService().get().getInternalServices();

    @Inject
    protected abstract ObjectFactory getObjects();

    @Classpath
    @InputArtifact
    public abstract Provider<FileSystemLocation> getInput();

    @Override
    public void transform(TransformOutputs outputs) {
        File input = getInput().get().getAsFile();
        if (!input.exists()) {
            // Files can be passed to the artifact transform even if they don't exist,
            // in the case when user adds a file classpath via files("path/to/jar").
            // Unfortunately we don't filter them out before the artifact transform is run.
            return;
        }

        // Output original file if it's safe to load from cache loader ELSE copy an entry
        if (input.isDirectory()) {
            // Directories are ok to use outside the cache, since they are not locked by the daemon.
            outputs.dir(input);
        } else if (internalServices.get().getGlobalCacheLocations().isInsideGlobalCache(input.getAbsolutePath())) {
            // Jars that are already in the global cache don't need to be copied, since
            // the global caches are additive only and jars shouldn't be deleted or changed during the build.
            outputs.file(input);
        } else {
            createInstrumentationClasspathMarker(outputs);
            // Jars that are in some mutable location (e.g. build/ directory) need to be copied to the global cache,
            // since daemon keeps them locked when loading them to a classloader, which prevents e.g. deleting the build directory on windows
            File copyOfOriginalFile = outputs.file(ORIGINAL_DIR_NAME + "/" + input.getName());
            GFileUtils.copyFile(input, copyOfOriginalFile);
        }
    }
}
