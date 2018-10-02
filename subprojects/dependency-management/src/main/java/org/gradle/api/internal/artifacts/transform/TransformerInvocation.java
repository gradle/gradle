/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.api.artifacts.transform.ArtifactTransform;
import org.gradle.api.internal.changedetection.state.isolation.Isolatable;
import org.gradle.internal.hash.HashCode;

import java.io.File;
import java.util.List;

public class TransformerInvocation {
    private final Class<? extends ArtifactTransform> implementationClass;
    private final Isolatable<Object[]> parameters;

    public Class<? extends ArtifactTransform> getImplementationClass() {
        return implementationClass;
    }

    public Isolatable<Object[]> getParameters() {
        return parameters;
    }

    public File getPrimaryInput() {
        return primaryInput;
    }

    private final File primaryInput;
    private List<File> result;

    public TransformerInvocation(Class<? extends ArtifactTransform> implementationClass, Isolatable<Object[]> parameters, File primaryInput) {
        this.implementationClass = implementationClass;
        this.parameters = parameters;
        this.primaryInput = primaryInput;
    }

    public List<File> getResult() {
        return result;
    }

    public void setResult(List<File> result) {
        this.result = result;
    }

    public HashCode getSecondaryInputsHash() {
        return null;
    }

    public ArtifactTransform getTransformer() {
        return null;
    }
}
