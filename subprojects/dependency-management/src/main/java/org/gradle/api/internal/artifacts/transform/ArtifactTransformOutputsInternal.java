/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.internal.artifacts.transform;

import com.google.common.collect.ImmutableList;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.transform.ArtifactTransformOutputs;

import java.io.File;

public interface ArtifactTransformOutputsInternal extends ArtifactTransformOutputs {

    static OutputLocationType determineOutputLocationType(File output, File primaryInput, String primaryInputPrefix, File outputDir, String outputDirPrefix) {
        if (output.equals(primaryInput)) {
            return OutputLocationType.INPUT_ARTIFACT;
        }
        if (output.equals(outputDir)) {
            return OutputLocationType.WORKSPACE;
        }
        if (output.getPath().startsWith(outputDirPrefix)) {
            return OutputLocationType.WORKSPACE;
        }
        if (output.getPath().startsWith(primaryInputPrefix)) {
            return OutputLocationType.INPUT_ARTIFACT;
        }
        throw new InvalidUserDataException("Transform output " + output.getPath() + " must be a part of the input artifact or refer to a relative path.");
    }

    static void validateOutputExists(File output) {
        if (!output.exists()) {
            throw new InvalidUserDataException("Transform output " + output.getPath() + " must exist.");
        }
    }

    ImmutableList<File> getRegisteredOutputs();

    enum OutputLocationType {
        INPUT_ARTIFACT, WORKSPACE
    }
}
