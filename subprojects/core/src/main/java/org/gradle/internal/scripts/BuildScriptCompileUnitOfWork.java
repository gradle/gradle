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
import java.io.File;
import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * A base class that represents a work for compilation for Kotlin and Groovy scripts.
 */
public abstract class BuildScriptCompileUnitOfWork implements ImmutableUnitOfWork {

    private final ImmutableWorkspaceProvider workspaceProvider;
    private final FileCollectionFactory fileCollectionFactory;
    private final InputFingerprinter inputFingerprinter;

    public BuildScriptCompileUnitOfWork(
        ImmutableWorkspaceProvider workspaceProvider,
        FileCollectionFactory fileCollectionFactory,
        InputFingerprinter inputFingerprinter
    ) {
        this.workspaceProvider = workspaceProvider;
        this.fileCollectionFactory = fileCollectionFactory;
        this.inputFingerprinter = inputFingerprinter;
    }

    @Override
    public abstract void visitIdentityInputs(InputVisitor visitor);

    @Override
    public abstract String getDisplayName();

    public abstract void compileTo(File classesDir);

    @Override
    public ImmutableWorkspaceProvider getWorkspaceProvider() {
        return workspaceProvider;
    }

    @Override
    public Identity identify(Map<String, ValueSnapshot> identityInputs, Map<String, CurrentFileCollectionFingerprint> identityFileInputs) {
        Hasher hasher = Hashing.newHasher();
        identityInputs.values().forEach(value -> requireNonNull(value).appendToHasher(hasher));
        String identity = hasher.hash().toString();
        return () -> identity;
    }

    @Override
    public WorkOutput execute(ExecutionRequest executionRequest) {
        File workspace = executionRequest.getWorkspace();
        compileTo(classesDir(workspace));
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

    @Nullable
    @Override
    public Object loadAlreadyProducedOutput(File workspace) {
        return classesDir(workspace);
    }

    @Override
    public InputFingerprinter getInputFingerprinter() {
        return inputFingerprinter;
    }

    @Override
    public void visitOutputs(File workspace, OutputVisitor visitor) {
        File classesDir = classesDir(workspace);
        OutputFileValueSupplier outputValueSupplier = OutputFileValueSupplier.fromStatic(classesDir, fileCollectionFactory.fixed(classesDir));
        visitor.visitOutputProperty("classesDir", TreeType.DIRECTORY, outputValueSupplier);
    }

    private static File classesDir(File workspace) {
        return new File(workspace, "classes");
    }
}
