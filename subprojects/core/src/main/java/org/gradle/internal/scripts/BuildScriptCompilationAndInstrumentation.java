/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.scripts;

import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.internal.classpath.transforms.ClasspathElementTransform;
import org.gradle.internal.classpath.transforms.ClasspathElementTransformFactoryForLegacy;
import org.gradle.internal.classpath.transforms.InstrumentingClassTransform;
import org.gradle.internal.classpath.types.InstrumentingTypeRegistry;
import org.gradle.internal.execution.ImmutableUnitOfWork;
import org.gradle.internal.execution.InputFingerprinter;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.workspace.ImmutableWorkspaceProvider;
import org.gradle.internal.file.TreeType;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.hash.Hashing;
import org.gradle.internal.snapshot.ValueSnapshot;

import javax.annotation.Nullable;
import javax.annotation.OverridingMethodsMustInvokeSuper;
import java.io.File;
import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * A base class that represents a work for compilation for Kotlin and Groovy build scripts.
 * This work unit first compiles the build script to a directory, and then instruments the directory for configuration cache and returns instrumented output.
 */
public abstract class BuildScriptCompilationAndInstrumentation implements ImmutableUnitOfWork {

    private final ImmutableWorkspaceProvider workspaceProvider;
    private final InputFingerprinter inputFingerprinter;
    private final ClasspathElementTransformFactoryForLegacy transformFactory;
    protected final FileCollectionFactory fileCollectionFactory;

    public BuildScriptCompilationAndInstrumentation(
        ImmutableWorkspaceProvider workspaceProvider,
        FileCollectionFactory fileCollectionFactory,
        InputFingerprinter inputFingerprinter,
        ClasspathElementTransformFactoryForLegacy transformFactory
    ) {
        this.workspaceProvider = workspaceProvider;
        this.fileCollectionFactory = fileCollectionFactory;
        this.inputFingerprinter = inputFingerprinter;
        this.transformFactory = transformFactory;
    }

    @Override
    public abstract void visitIdentityInputs(InputVisitor visitor);

    /**
     * A compile operation. It should return a File where classes are compiled to.
     */
    protected abstract File compile(File workspace);

    /**
     * Provides a File where instrumented jar will be written to.
     */
    protected abstract File instrumentedJar(File workspace);

    @Override
    public Identity identify(Map<String, ValueSnapshot> identityInputs, Map<String, CurrentFileCollectionFingerprint> identityFileInputs) {
        Hasher hasher = Hashing.newHasher();
        identityInputs.values().forEach(value -> requireNonNull(value).appendToHasher(hasher));
        String identity = hasher.hash().toString();
        return () -> identity;
    }

    @Override
    @OverridingMethodsMustInvokeSuper
    public void visitOutputs(File workspace, OutputVisitor visitor) {
        File instrumentedJar = instrumentedJar(workspace);
        OutputFileValueSupplier instrumentedJarValue = OutputFileValueSupplier.fromStatic(instrumentedJar, fileCollectionFactory.fixed(instrumentedJar));
        visitor.visitOutputProperty("instrumentedJar", TreeType.FILE, instrumentedJarValue);
    }

    @Override
    public WorkOutput execute(ExecutionRequest executionRequest) {
        File workspace = executionRequest.getWorkspace();
        File compileOutput = compile(workspace);
        // We should instrument output of classes directory to the classes directory instead of a jar,
        // but currently instrumentation classloader doesn't support that yet.
        instrument(compileOutput, instrumentedJar(workspace));
        return new UnitOfWork.WorkOutput() {
            @Override
            public WorkResult getDidWork() {
                return UnitOfWork.WorkResult.DID_WORK;
            }

            @Nullable
            @Override
            public Object getOutput(File workspace) {
                return loadAlreadyProducedOutput(workspace);
            }
        };
    }

    private void instrument(File sourceDir, File destination) {
        ClasspathElementTransform transform = transformFactory.createTransformer(sourceDir, new InstrumentingClassTransform(), InstrumentingTypeRegistry.EMPTY);
        transform.transform(destination);
    }

    @Nullable
    @Override
    public Object loadAlreadyProducedOutput(File workspace) {
        return instrumentedJar(workspace);
    }

    @Override
    public InputFingerprinter getInputFingerprinter() {
        return inputFingerprinter;
    }

    @Override
    public ImmutableWorkspaceProvider getWorkspaceProvider() {
        return workspaceProvider;
    }

    @Override
    public abstract String getDisplayName();
}
