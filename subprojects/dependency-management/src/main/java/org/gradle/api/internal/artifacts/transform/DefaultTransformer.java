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

import com.google.common.collect.ImmutableSet;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.transform.ArtifactTransform;
import org.gradle.api.artifacts.transform.ArtifactTransformDependencies;
import org.gradle.api.artifacts.transform.PrimaryInput;
import org.gradle.api.artifacts.transform.Workspace;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.instantiation.InstanceFactory;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.isolation.Isolatable;
import org.gradle.internal.service.ServiceLookup;
import org.gradle.internal.service.ServiceLookupException;
import org.gradle.internal.service.UnknownServiceException;

import javax.annotation.Nullable;
import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.List;

public class DefaultTransformer implements Transformer {

    private final Class<? extends ArtifactTransform> implementationClass;
    private final Isolatable<?> config;
    private final boolean requiresDependencies;
    private final Isolatable<Object[]> parameters;
    private final InstanceFactory<? extends ArtifactTransform> instanceFactory;
    private final HashCode inputsHash;
    private final ImmutableAttributes fromAttributes;

    public DefaultTransformer(Class<? extends ArtifactTransform> implementationClass, Isolatable<?> config, Isolatable<Object[]> parameters, HashCode inputsHash, InstantiatorFactory instantiatorFactory, ImmutableAttributes fromAttributes) {
        this.implementationClass = implementationClass;
        this.config = config;
        this.instanceFactory = instantiatorFactory.injectScheme(ImmutableSet.of(Workspace.class, PrimaryInput.class)).forType(implementationClass);
        this.requiresDependencies = instanceFactory.requiresService(ArtifactTransformDependencies.class);
        this.parameters = parameters;
        this.inputsHash = inputsHash;
        this.fromAttributes = fromAttributes;
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
        ArtifactTransform transformer = newTransformer(primaryInput, outputDir, dependencies);
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

    private ArtifactTransform newTransformer(File inputFile, File outputDir, ArtifactTransformDependencies artifactTransformDependencies) {
        ServiceLookup services = new TransformServiceLookup(inputFile, outputDir, config.isolate(), requiresDependencies ? artifactTransformDependencies : null);
        return instanceFactory.newInstance(services, parameters.isolate());
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

    private static class TransformServiceLookup implements ServiceLookup {
        private final File inputFile;
        private final File outputDir;
        private final Object config;
        private final ArtifactTransformDependencies artifactTransformDependencies;

        public TransformServiceLookup(File inputFile, File outputDir, @Nullable Object config, @Nullable ArtifactTransformDependencies artifactTransformDependencies) {
            this.inputFile = inputFile;
            this.outputDir = outputDir;
            this.config = config;
            this.artifactTransformDependencies = artifactTransformDependencies;
        }

        @Nullable
        private
        Object find(Type serviceType, @Nullable Class<? extends Annotation> annotatedWith) {
            if (!(serviceType instanceof Class)) {
                return null;
            }
            Class<?> serviceClass = (Class<?>) serviceType;
            if (annotatedWith == Workspace.class && serviceClass.isAssignableFrom(File.class)) {
                return outputDir;
            }
            if (annotatedWith == PrimaryInput.class && serviceClass.isAssignableFrom(File.class)) {
                return inputFile;
            }
            if (annotatedWith == null && artifactTransformDependencies != null && serviceClass.isAssignableFrom(ArtifactTransformDependencies.class)) {
                return artifactTransformDependencies;
            }
            if (annotatedWith == null && serviceClass.isInstance(config)) {
                return config;
            }
            return null;
        }

        @Nullable
        @Override
        public Object find(Type serviceType) throws ServiceLookupException {
            return find(serviceType, null);
        }

        @Override
        public Object get(Type serviceType) throws UnknownServiceException, ServiceLookupException {
            Object result = find(serviceType);
            if (result == null) {
                throw new UnknownServiceException(serviceType, "No service of type " + serviceType + " available.");
            }
            return result;
        }

        @Override
        public Object get(Type serviceType, Class<? extends Annotation> annotatedWith) throws UnknownServiceException, ServiceLookupException {
            Object result = find(serviceType, annotatedWith);
            if (result == null) {
                throw new UnknownServiceException(serviceType, "No service of type " + serviceType + " available.");
            }
            return result;
        }
    }
}
