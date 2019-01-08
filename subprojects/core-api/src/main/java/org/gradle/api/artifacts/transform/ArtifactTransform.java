/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.artifacts.transform;

import java.io.File;
import java.util.List;

/**
 * Base class for artifact transforms.
 *
 * <p>Implementations must provide a public constructor. The constructor may optionally accept parameters, in which case it must be annotated with {@link javax.inject.Inject}. The following parameters are available:</p>
 *
 * <ul>
 * <li>{@link ArtifactTransformDependencies} parameter to receive the dependencies of the file to be transformed.</li>
 * <li>The objects provided to {@link org.gradle.api.ActionConfiguration#setParams(Object...)}.</li>
 * </ul>
 *
 * <p>A property annotated with {@link javax.inject.Inject} and whose type is {@link ArtifactTransformDependencies} will receive the dependencies of the file to be transformed.
 *
 * <p>A property annotated with {@link PrimaryInput} will receive the <em>primary input</em> location, which is the file or directory that the transform should be applied to.
 *
 * <p>A property annotated with {@link Workspace} will receive the <em>workspace</em> location, which is the directory that the transform should write its output files to.
 *
 * @since 3.4
 */
public abstract class ArtifactTransform {
    private File outputDirectory;

    public File getOutputDirectory() {
        return outputDirectory;
    }

    public void setOutputDirectory(File outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    public abstract List<File> transform(File input);
}
