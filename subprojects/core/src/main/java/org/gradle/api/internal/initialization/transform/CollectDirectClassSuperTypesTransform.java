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

import com.google.common.io.Files;
import org.gradle.api.artifacts.transform.InputArtifact;
import org.gradle.api.artifacts.transform.TransformAction;
import org.gradle.api.artifacts.transform.TransformOutputs;
import org.gradle.api.artifacts.transform.TransformParameters;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Classpath;
import org.gradle.work.DisableCachingByDefault;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * TODO: This class has similar implementation in build-logic/packaging/src/main/kotlin/gradlebuild/instrumentation/transforms/CollectDirectClassSuperTypesTransform.kt.
 *  We could reuse the same class at some point.
 */
@DisableCachingByDefault(because = "Not enable yet, since original instrumentation is also not cached in build cache.")
public abstract class CollectDirectClassSuperTypesTransform implements TransformAction<TransformParameters.None> {

    private static final String DIRECT_SUPER_TYPES_FILE = "direct-super-types.properties";

    @InputArtifact
    @Classpath
    public abstract Provider<FileSystemLocation> getInput();

    private File getInputAsFile() {
        return getInput().get().getAsFile();
    }

    public void transform(TransformOutputs outputs) {
        try {
            File file = outputs.file(DIRECT_SUPER_TYPES_FILE);
            Files.write("".getBytes(StandardCharsets.UTF_8), file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
