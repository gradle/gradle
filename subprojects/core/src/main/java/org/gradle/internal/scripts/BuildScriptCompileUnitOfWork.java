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
import java.io.File;
import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * A base class that represents a work for compilation for Kotlin and Groovy build scripts.
 * This work unit first compiles the build script to a directory, and then instruments the directory for configuration cache and returns instrumented output.
 */
public abstract class BuildScriptCompileUnitOfWork implements ImmutableUnitOfWork {

    private final ImmutableWorkspaceProvider workspaceProvider;
    private final FileCollectionFactory fileCollectionFactory;
    private final InputFingerprinter inputFingerprinter;
    private final ClasspathElementTransformFactoryForLegacy transformFactory;

    public BuildScriptCompileUnitOfWork(
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

    @Override
    public abstract String getDisplayName();

    /**
     * Compiles the build script using given compile workspace and returns the final output location.
     */
    public abstract File compileTo(File compileWorkspace);

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
        File output = compileTo(originalDir(workspace));
        // We should instrument output of classes directory to the classes directory instead of a jar,
        // but currently instrumentation classloader doesn't support that yet.
        instrument(output, transformedJar(workspace));
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
        return new BuildScriptCompilationOutput(originalDir(workspace), transformedJar(workspace));
    }

    @Override
    public InputFingerprinter getInputFingerprinter() {
        return inputFingerprinter;
    }

    @Override
    public void visitOutputs(File workspace, OutputVisitor visitor) {
        File originalDir = originalDir(workspace);
        OutputFileValueSupplier originalDirValue = OutputFileValueSupplier.fromStatic(originalDir, fileCollectionFactory.fixed(originalDir));
        visitor.visitOutputProperty("originalDir", TreeType.DIRECTORY, originalDirValue);

        File transformedDir = transformedDir(workspace);
        OutputFileValueSupplier transformedDirValue = OutputFileValueSupplier.fromStatic(transformedDir, fileCollectionFactory.fixed(transformedDir));
        visitor.visitOutputProperty("transformedDir", TreeType.FILE, transformedDirValue);
    }

    private static File originalDir(File workspace) {
        return new File(workspace, "original");
    }

    private static File transformedDir(File workspace) {
        return new File(workspace, "transformed");
    }

    private static File transformedJar(File workspace) {
        return new File(transformedDir(workspace), "transformed.jar");
    }

    public static class BuildScriptCompilationOutput {

        private final File originalDir;
        private final File output;

        public BuildScriptCompilationOutput(File originalDir, File output) {
            this.originalDir = originalDir;
            this.output = output;
        }

        public File getOutput() {
            return output;
        }

        public File getOriginalDir() {
            return originalDir;
        }
    }
}
