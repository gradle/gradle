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
import org.gradle.api.artifacts.transform.VariantTransformConfigurationException;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.classloader.ClassLoaderHierarchyHasher;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.isolation.Isolatable;
import org.gradle.internal.isolation.IsolatableFactory;
import org.gradle.model.internal.type.ModelType;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Arrays;

public abstract class AbstractTransformer<T> implements Transformer {
    private final Class<? extends T> implementationClass;
    private final ImmutableAttributes fromAttributes;

    public AbstractTransformer(Class<? extends T> implementationClass, ImmutableAttributes fromAttributes) {
        this.implementationClass = implementationClass;
        this.fromAttributes = fromAttributes;
    }

    @Override
    public ImmutableAttributes getFromAttributes() {
        return fromAttributes;
    }

    protected static ImmutableList<File> validateOutputs(File primaryInput, File outputDir, ImmutableList<File> outputs) {
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

    @Override
    public Class<? extends T> getImplementationClass() {
        return implementationClass;
    }

    @Override
    public String getDisplayName() {
        return implementationClass.getSimpleName();
    }

    protected static void appendActionImplementation(Class<?> implementation, Hasher hasher, ClassLoaderHierarchyHasher classLoaderHierarchyHasher) {
        hasher.putString(implementation.getName());
        hasher.putHash(classLoaderHierarchyHasher.getClassLoaderHash(implementation.getClassLoader()));
    }

    protected static <T> Isolatable<T> isolateParameters(@Nullable T parameters, Class<?> implementationClass, IsolatableFactory isolatableFactory) {
        try {
            return isolatableFactory.isolate(parameters);
        } catch (Exception e) {
            throw new VariantTransformConfigurationException(String.format("Could not snapshot parameters values for transform %s: %s", ModelType.of(implementationClass).getDisplayName(), formatParameters(parameters)), e);
        }
    }

    @Nullable
    private static Object formatParameters(@Nullable Object parameters) {
        if (parameters instanceof Object[]) {
            return Arrays.toString((Object[]) parameters);
        } else {
            return parameters;
        }
    }
}
