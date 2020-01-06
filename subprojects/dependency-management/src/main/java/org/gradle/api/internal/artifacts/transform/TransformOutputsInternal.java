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
import org.gradle.api.artifacts.transform.TransformOutputs;

import java.io.File;

public interface TransformOutputsInternal extends TransformOutputs {

    static OutputLocationType determineOutputLocationType(File output, File inputArtifact, String inputArtifactPrefix, File outputDir, String outputDirPrefix) {
        if (output.equals(inputArtifact)) {
            return OutputLocationType.INPUT_ARTIFACT;
        }
        if (output.equals(outputDir)) {
            return OutputLocationType.WORKSPACE;
        }
        if (output.getPath().startsWith(outputDirPrefix)) {
            return OutputLocationType.WORKSPACE;
        }
        if (output.getPath().startsWith(inputArtifactPrefix)) {
            return OutputLocationType.INPUT_ARTIFACT;
        }
        throw new InvalidUserDataException("Transform output " + output.getPath() + " must be a part of the input artifact or refer to a relative path.");
    }

    static void validateOutputExists(String outputDirPrefix, File output) {
        if (!output.exists()) {
            String outputAbsolutePath = output.getAbsolutePath();
            String reportedPath = outputAbsolutePath.startsWith(outputDirPrefix)
                ? outputAbsolutePath.substring(outputDirPrefix.length())
                : outputAbsolutePath;
            throw new InvalidUserDataException("Transform output " + reportedPath + " must exist.");
        }
    }

    ImmutableList<File> getRegisteredOutputs();

    enum OutputLocationType {
        INPUT_ARTIFACT, WORKSPACE
    }
}
