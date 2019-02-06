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
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.internal.classloader.ClassLoaderHierarchyHasher;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.isolation.Isolatable;

import java.io.File;

public abstract class AbstractTransformer<T, P> implements Transformer {
    private final Class<? extends T> implementationClass;
    private final ImmutableAttributes fromAttributes;
    private final DomainObjectContextProjectStateHandler projectStateHandler;
    private IsolatableParameters<P> isolatableParameters;
    private final ProjectStateRegistry.SafeExclusiveLock isolationLock;

    public AbstractTransformer(Class<? extends T> implementationClass, ImmutableAttributes fromAttributes, DomainObjectContextProjectStateHandler projectStateHandler) {
        this.implementationClass = implementationClass;
        this.fromAttributes = fromAttributes;
        this.projectStateHandler = projectStateHandler;
        this.isolationLock = projectStateHandler.newExclusiveOperationLock();
    }

    static class IsolatableParameters<T> {
        private HashCode secondaryInputsHash;
        private Isolatable<T> isolatableParameters;

        public IsolatableParameters(Isolatable<T> isolatableParameters, HashCode secondaryInputsHash) {
            this.secondaryInputsHash = secondaryInputsHash;
            this.isolatableParameters = isolatableParameters;
        }

        public HashCode getSecondaryInputsHash() {
            return secondaryInputsHash;
        }

        public Isolatable<T> getIsolatableParameters() {
            return isolatableParameters;
        }
    }

    @Override
    public ImmutableAttributes getFromAttributes() {
        return fromAttributes;
    }

    abstract protected IsolatableParameters<P> doIsolateParameters();

    @Override
    public void isolateParameters() {
        if (isolatableParameters == null) {
            if (!projectStateHandler.hasMutableProjectState()) {
                projectStateHandler.withLenientState(this::isolateExclusively);
            } else {
                isolateExclusively();
            }
        }
    }

    private void isolateExclusively() {
        isolationLock.withLock(() -> {
            if (isolatableParameters != null) {
                return;
            }
            isolatableParameters = doIsolateParameters();
        });
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

    protected static void appendActionImplementation(ClassLoaderHierarchyHasher classLoaderHierarchyHasher, Hasher hasher, Class<?> implementation) {
        hasher.putString(implementation.getName());
        hasher.putHash(classLoaderHierarchyHasher.getClassLoaderHash(implementation.getClassLoader()));
    }

    @Override
    public HashCode getSecondaryInputHash() {
        return getIsolated().getSecondaryInputsHash();
    }

    public IsolatableParameters<P> getIsolated() {
        if (isolatableParameters == null) {
            throw new IllegalStateException("The parameters of " + getDisplayName() + "need to be isolated first!");
        }
        return isolatableParameters;
    }
}
