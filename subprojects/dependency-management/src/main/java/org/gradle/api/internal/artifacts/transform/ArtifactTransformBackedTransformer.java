/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.transform.ArtifactTransform;
import org.gradle.api.internal.changedetection.state.isolation.Isolatable;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.util.BiFunction;

import java.io.File;
import java.util.List;

class ArtifactTransformBackedTransformer implements BiFunction<List<File>, File, File> {
    private final Class<? extends ArtifactTransform> implementationClass;
    private final Instantiator instantiator;
    private final Isolatable<Object[]> parameters;

    ArtifactTransformBackedTransformer(Class<? extends ArtifactTransform> implementationClass, Isolatable<Object[]> parameters, Instantiator instantiator) {
        this.implementationClass = implementationClass;
        this.parameters = parameters;
        this.instantiator = instantiator;
    }

    @Override
    public List<File> apply(File file, File outputDir) {
        ArtifactTransform artifactTransform = instantiator.newInstance(implementationClass, parameters.isolate());
        artifactTransform.setOutputDirectory(outputDir);
        List<File> outputs = artifactTransform.transform(file);
        if (outputs == null) {
            throw new InvalidUserDataException("Transform returned null result.");
        }
        String inputFilePrefix = file.getPath() + File.separator;
        String outputDirPrefix = outputDir.getPath() + File.separator;
        for (File output : outputs) {
            if (!output.exists()) {
                throw new InvalidUserDataException("Transform output file " + output.getPath() + " does not exist.");
            }
            if (output.equals(file) || output.equals(outputDir)) {
                continue;
            }
            if (output.getPath().startsWith(outputDirPrefix)) {
                continue;
            }
            if (output.getPath().startsWith(inputFilePrefix)) {
                continue;
            }
            throw new InvalidUserDataException("Transform output file " + output.getPath() + " is not a child of the transform's input file or output directory.");
        }
        return outputs;
    }
}
