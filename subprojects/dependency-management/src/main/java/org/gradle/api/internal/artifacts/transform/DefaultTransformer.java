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
import org.gradle.api.artifacts.transform.ArtifactTransformDependencies;
import org.gradle.api.internal.InjectUtil;
import org.gradle.api.internal.InstantiatorFactory;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.isolation.Isolatable;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.DefaultServiceRegistry;

import javax.annotation.Nullable;
import java.io.File;
import java.lang.reflect.Constructor;
import java.util.List;

public class DefaultTransformer implements Transformer {

    private final Class<? extends ArtifactTransform> implementationClass;
    private final boolean requiresDependencies;
    private final Isolatable<Object[]> parameters;
    private final InstantiatorFactory instantiatorFactory;
    private final HashCode inputsHash;
    private final ImmutableAttributes fromAttributes;

    public DefaultTransformer(Class<? extends ArtifactTransform> implementationClass, Isolatable<Object[]> parameters, HashCode inputsHash, InstantiatorFactory instantiatorFactory, ImmutableAttributes fromAttributes) {
        this.implementationClass = implementationClass;
        this.requiresDependencies = hasDependenciesAmongConstructorParameters(implementationClass);
        this.parameters = parameters;
        this.instantiatorFactory = instantiatorFactory;
        this.inputsHash = inputsHash;
        this.fromAttributes = fromAttributes;
    }

    private static boolean hasDependenciesAmongConstructorParameters(Class<? extends ArtifactTransform> implementation) {
        Constructor<?> constructor = InjectUtil.selectConstructor(implementation);
        for (Class<?> parameterType : constructor.getParameterTypes()) {
            if (ArtifactTransformDependencies.class.equals(parameterType)) {
                return true;
            }
        }
        return false;
    }

    public boolean requiresDependencies() {
        return requiresDependencies;
    }

    @Override
    public ImmutableAttributes getFromAttributes() {
        return fromAttributes;
    }

    @Override
    public List<File> transform(File primaryInput, File outputDir, ArtifactTransformDependencies dependencies) {
        ArtifactTransform transformer = newTransformer(dependencies);
        transformer.setOutputDirectory(outputDir);
        List<File> outputs = transformer.transform(primaryInput);
        return validateOutputs(primaryInput, outputDir, outputs);
    }

    private static List<File> validateOutputs(File primaryInput, File outputDir, @Nullable List<File> outputs) {
        if (outputs == null) {
            throw new InvalidUserDataException("Transform returned null result.");
        }
        String inputFilePrefix = primaryInput.getPath() + File.separator;
        String outputDirPrefix = outputDir.getPath() + File.separator;
        for (File output : outputs) {
            if (!output.exists()) {
                throw new InvalidUserDataException("Transform output file " + output.getPath() + " does not exist.");
            }
            if (output.equals(primaryInput) || output.equals(outputDir)) {
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

    private ArtifactTransform newTransformer(ArtifactTransformDependencies artifactTransformDependencies) {
        Instantiator instantiator;
        if (requiresDependencies) {
            DefaultServiceRegistry registry = new DefaultServiceRegistry();
            registry.add(ArtifactTransformDependencies.class, artifactTransformDependencies);
            instantiator = instantiatorFactory.inject(registry);
        } else {
            instantiator = instantiatorFactory.inject();
        }
        return instantiator.newInstance(implementationClass, parameters.isolate());
    }

    @Override
    public HashCode getSecondaryInputHash() {
        return inputsHash;
    }

    @Override
    public Class<? extends ArtifactTransform> getImplementationClass() {
        return implementationClass;
    }

    @Override
    public String getDisplayName() {
        return implementationClass.getSimpleName();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DefaultTransformer that = (DefaultTransformer) o;

        return inputsHash.equals(that.inputsHash);
    }

    @Override
    public int hashCode() {
        return inputsHash.hashCode();
    }
}
