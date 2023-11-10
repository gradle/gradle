/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.execution

import com.google.common.collect.Maps
import groovy.transform.CompileStatic
import groovy.transform.Immutable
import org.gradle.api.internal.file.TestFiles
import org.gradle.internal.execution.history.ExecutionHistoryStore
import org.gradle.internal.execution.model.InputNormalizer
import org.gradle.internal.execution.workspace.MutableWorkspaceProvider
import org.gradle.internal.file.TreeType
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint
import org.gradle.internal.fingerprint.DirectorySensitivity
import org.gradle.internal.fingerprint.LineEndingSensitivity
import org.gradle.internal.hash.TestHashCodes
import org.gradle.internal.snapshot.ValueSnapshot
import org.gradle.internal.snapshot.impl.ImplementationSnapshot
import org.gradle.test.fixtures.file.TestFile

import java.util.function.Consumer
import java.util.function.Supplier

import static org.gradle.internal.properties.InputBehavior.NON_INCREMENTAL

@CompileStatic
class MutableUnitOfWorkBuilder {
    private Supplier<UnitOfWork.WorkResult> work = { ->
        create.each { it ->
            it.createFile()
        }
        return UnitOfWork.WorkResult.DID_WORK
    }
    private Map<String, Object> inputProperties
    private Map<String, ? extends Collection<? extends File>> inputFiles
    private Map<String, ? extends File> outputFiles
    private Map<String, ? extends File> outputDirs
    private Collection<? extends TestFile> create
    private ImplementationSnapshot implementation = ImplementationSnapshot.of(UnitOfWork.name, TestHashCodes.hashCodeFrom(1234))
    private Consumer<WorkValidationContext> validator

    private final InputFingerprinter inputFingerprinter
    private final ExecutionHistoryStore executionHistoryStore

    MutableUnitOfWorkBuilder(
        Map<String, Object> inputProperties,
        Map<String, ? extends Collection<? extends File>> inputFiles,
        Map<String, ? extends File> outputFiles,
        Map<String, ? extends File> outputDirs,
        Collection<? extends TestFile> create,

        InputFingerprinter inputFingerprinter,
        ExecutionHistoryStore executionHistoryStore
    ) {
        this.inputProperties = inputProperties
        this.inputFiles = inputFiles
        this.outputFiles = outputFiles
        this.outputDirs = outputDirs
        this.create = create

        this.inputFingerprinter = inputFingerprinter
        this.executionHistoryStore = executionHistoryStore
    }

    MutableUnitOfWorkBuilder withWork(Supplier<UnitOfWork.WorkResult> closure) {
        work = closure
        return this
    }

    MutableUnitOfWorkBuilder withInputFiles(Map<String, ? extends Collection<? extends File>> files) {
        this.inputFiles = files
        return this
    }

    MutableUnitOfWorkBuilder withoutInputFiles() {
        this.inputFiles = [:]
        return this
    }

    MutableUnitOfWorkBuilder withoutInputProperties() {
        this.inputProperties = [:]
        return this
    }

    MutableUnitOfWorkBuilder withOutputFiles(File... outputFiles) {
        return withOutputFiles((outputFiles as List)
            .withIndex()
            .collectEntries { outputFile, index -> [('defaultFiles' + index): outputFile] }
        )
    }

    MutableUnitOfWorkBuilder withOutputFiles(Map<String, ? extends File> files) {
        this.outputFiles = files
        return this
    }

    MutableUnitOfWorkBuilder withOutputDirs(File... outputDirs) {
        return withOutputDirs((outputDirs as List)
            .withIndex()
            .collectEntries { outputFile, index -> [('defaultDir' + index): outputFile] }
        )
    }

    MutableUnitOfWorkBuilder withOutputDirs(Map<String, ? extends File> dirs) {
        this.outputDirs = dirs
        return this
    }

    MutableUnitOfWorkBuilder createsFiles(TestFile... outputFiles) {
        create = Arrays.asList(outputFiles)
        return this
    }

    MutableUnitOfWorkBuilder withImplementation(ImplementationSnapshot implementation) {
        this.implementation = implementation
        return this
    }

    MutableUnitOfWorkBuilder withProperty(String name, Object value) {
        inputProperties.put(name, value)
        return this
    }

    MutableUnitOfWorkBuilder withValidator(Consumer<WorkValidationContext> validator) {
        this.validator = validator
        return this
    }

    @Immutable
    private static class SimpleIdentity implements UnitOfWork.Identity {
        final String uniqueId
    }

    MutableUnitOfWork build() {
        Map<String, OutputPropertySpec> outputFileSpecs = Maps.transformEntries(outputFiles, { key, value -> outputFileSpec(value) })
        Map<String, OutputPropertySpec> outputDirSpecs = Maps.transformEntries(outputDirs, { key, value -> outputDirectorySpec(value) })
        Map<String, OutputPropertySpec> outputs = outputFileSpecs + outputDirSpecs

        return new MutableUnitOfWork() {
            boolean executed

            @Override
            UnitOfWork.Identity identify(Map<String, ValueSnapshot> identityInputs, Map<String, CurrentFileCollectionFingerprint> identityFileInputs) {
                new SimpleIdentity("myId")
            }

            @Override
            MutableWorkspaceProvider getWorkspaceProvider() {
                new MutableWorkspaceProvider() {
                    @Override
                    <T> T withWorkspace(String path, MutableWorkspaceProvider.WorkspaceAction<T> action) {
                        return action.executeInWorkspace(null, executionHistoryStore)
                    }
                }
            }

            @Override
            InputFingerprinter getInputFingerprinter() {
                MutableUnitOfWorkBuilder.this.inputFingerprinter
            }

            @Override
            UnitOfWork.WorkOutput execute(UnitOfWork.ExecutionRequest executionRequest) {
                def didWork = work.get()
                executed = true
                return new UnitOfWork.WorkOutput() {
                    @Override
                    UnitOfWork.WorkResult getDidWork() {
                        return didWork
                    }

                    @Override
                    Object getOutput(File workspace) {
                        return loadAlreadyProducedOutput(workspace)
                    }
                }
            }

            @Override
            Object loadAlreadyProducedOutput(File workspace) {
                return "output"
            }

            @Override
            void visitImplementations(UnitOfWork.ImplementationVisitor visitor) {
                visitor.visitImplementation(implementation)
                visitor.visitImplementation(Object)
            }

            @Override
            void visitRegularInputs(UnitOfWork.InputVisitor visitor) {
                inputProperties.each { propertyName, value ->
                    visitor.visitInputProperty(propertyName, () -> value)
                }
                for (entry in inputFiles.entrySet()) {
                    visitor.visitInputFileProperty(
                        entry.key,
                        NON_INCREMENTAL,
                        new UnitOfWork.InputFileValueSupplier(
                            entry.value,
                            InputNormalizer.ABSOLUTE_PATH,
                            DirectorySensitivity.DEFAULT,
                            LineEndingSensitivity.DEFAULT,
                            () -> TestFiles.fixed(entry.value)
                        )
                    )
                }
            }

            @Override
            void visitOutputs(File workspace, UnitOfWork.OutputVisitor visitor) {
                outputs.forEach { name, spec ->
                    visitor.visitOutputProperty(name, spec.treeType, UnitOfWork.OutputFileValueSupplier.fromStatic(spec.root, TestFiles.fixed(spec.root)))
                }
            }

            @Override
            void validate(WorkValidationContext validationContext) {
                validator?.accept(validationContext)
            }

            @Override
            boolean shouldCleanupOutputsOnNonIncrementalExecution() {
                return false
            }

            @Override
            String getDisplayName() {
                "Test unit of work"
            }
        }
    }

    static class OutputPropertySpec {
        File root
        TreeType treeType

        OutputPropertySpec(File root, TreeType treeType) {
            this.treeType = treeType
            this.root = root
        }
    }

    static OutputPropertySpec outputDirectorySpec(File dir) {
        return new OutputPropertySpec(dir, TreeType.DIRECTORY)
    }

    static OutputPropertySpec outputFileSpec(File file) {
        return new OutputPropertySpec(file, TreeType.FILE)
    }
}
