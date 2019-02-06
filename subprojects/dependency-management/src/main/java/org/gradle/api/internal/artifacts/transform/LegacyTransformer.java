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

import com.google.common.collect.ImmutableList;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.transform.ArtifactTransform;
import org.gradle.api.artifacts.transform.VariantTransformConfigurationException;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.classloader.ClassLoaderHierarchyHasher;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.hash.Hashing;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.isolation.Isolatable;
import org.gradle.internal.isolation.IsolatableFactory;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.model.internal.type.ModelType;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class LegacyTransformer extends AbstractTransformer<ArtifactTransform, Object[]> {

    private final Object[] parameters;
    private final ClassLoaderHierarchyHasher classLoaderHierarchyHasher;
    private final IsolatableFactory isolatableFactory;
    private final Instantiator instantiator;

    public LegacyTransformer(Class<? extends ArtifactTransform> implementationClass, Object[] parameters, InstantiatorFactory instantiatorFactory, ImmutableAttributes fromAttributes, ClassLoaderHierarchyHasher classLoaderHierarchyHasher, IsolatableFactory isolatableFactory, DomainObjectContextProjectStateHandler domainObjectContextProjectStateHandler) {
        super(implementationClass, fromAttributes, domainObjectContextProjectStateHandler);
        this.parameters = parameters;
        this.instantiator = instantiatorFactory.inject();
        this.classLoaderHierarchyHasher = classLoaderHierarchyHasher;
        this.isolatableFactory = isolatableFactory;
    }

    public boolean requiresDependencies() {
        return false;
    }

    @Override
    public List<File> transform(File primaryInput, File outputDir, ArtifactTransformDependencies dependencies) {
        ArtifactTransform transformer = newTransformer();
        transformer.setOutputDirectory(outputDir);
        List<File> outputs = transformer.transform(primaryInput);
        if (outputs == null) {
            throw new InvalidUserDataException("Transform returned null result.");
        }
        return validateOutputs(primaryInput, outputDir, ImmutableList.copyOf(outputs));
    }

    @Override
    protected IsolatableParameters<Object[]> doIsolateParameters() {
        Hasher hasher = Hashing.newHasher();
        appendActionImplementation(classLoaderHierarchyHasher, hasher, getImplementationClass());

        Isolatable<Object[]> isolatableParameters;
        try {
            isolatableParameters = isolatableFactory.isolate(parameters);
        } catch (Exception e) {
            throw new VariantTransformConfigurationException(String.format("Could not snapshot parameters values for transform %s: %s", ModelType.of(getImplementationClass()).getDisplayName(), Arrays.asList(parameters)), e);
        }
        isolatableParameters.appendToHasher(hasher);
        HashCode secondaryInputsHash = hasher.hash();
        return new IsolatableParameters<>(isolatableParameters, secondaryInputsHash);
    }

    private ArtifactTransform newTransformer() {
        Object[] isolatedParameters = getIsolated().getIsolatableParameters().isolate();
        return instantiator.newInstance(getImplementationClass(), isolatedParameters);
    }
}
