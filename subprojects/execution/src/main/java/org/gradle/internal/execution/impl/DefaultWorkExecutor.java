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

package org.gradle.internal.execution.impl;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.gradle.internal.MutableBoolean;
import org.gradle.internal.MutableReference;
import org.gradle.internal.classloader.ClassLoaderHasher;
import org.gradle.internal.execution.InputFileProperty;
import org.gradle.internal.execution.InputProperty;
import org.gradle.internal.execution.OutputFileProperty;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.WorkExecutor;
import org.gradle.internal.execution.WorkResult;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileCollectionFingerprinter;
import org.gradle.internal.fingerprint.FileCollectionFingerprinterRegistry;
import org.gradle.internal.fingerprint.OutputNormalizer;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.internal.snapshot.ValueSnapshotter;

import java.util.List;
import java.util.Map;

public class DefaultWorkExecutor implements WorkExecutor {
    private final ClassLoaderHasher classLoaderHasher;
    private final ValueSnapshotter valueSnapshotter;
    private final FileCollectionFingerprinterRegistry fingerprinterRegistry;

    public DefaultWorkExecutor(ClassLoaderHasher classLoaderHasher, ValueSnapshotter valueSnapshotter, FileCollectionFingerprinterRegistry fingerprinterRegistry) {
        this.classLoaderHasher = classLoaderHasher;
        this.valueSnapshotter = valueSnapshotter;
        this.fingerprinterRegistry = fingerprinterRegistry;
    }

    @Override
    public WorkResult execute(UnitOfWork work) {
        // TODO Use immutable structures
        final List<Class<?>> types = Lists.newArrayListWithCapacity(1);
        final Map<String, ValueSnapshot> inputSnapshots = Maps.newLinkedHashMap();
        final Map<String, FileCollectionFingerprint> inputFileFingerprints = Maps.newLinkedHashMap();
        final MutableReference<Boolean> hasEmptyPrimaryInputs = MutableReference.empty();
        work.visitInputs(new UnitOfWork.InputVisitor() {
            @Override
            public void visitType(Class<?> type) {
                types.add(type);
            }

            @Override
            public void visitInput(InputProperty input) {
                inputSnapshots.put(input.getName(), valueSnapshotter.isolatableSnapshot(input.getValue()));
            }

            @Override
            public void visitFileInput(InputFileProperty input) {
                FileCollectionFingerprinter fingerprinter = fingerprinterRegistry.getFingerprinter(input.getNormalizer());
                CurrentFileCollectionFingerprint fingerprint = fingerprinter.fingerprint(input.getFiles());
                inputFileFingerprints.put(input.getName(), fingerprint);
                if (input.isPrimaryInput() && hasEmptyPrimaryInputs.get() != Boolean.FALSE) {
                    hasEmptyPrimaryInputs.set(fingerprint.isEmpty());
                }
            }
        });

        final FileCollectionFingerprinter outputFingerprinter = fingerprinterRegistry.getFingerprinter(OutputNormalizer.class);
        Map<String, FileCollectionFingerprint> outputFileFingerprintsBeforeExecution = Maps.newLinkedHashMap();
        work.visitOutputs(new OutputFingerprinter(outputFingerprinter, outputFileFingerprintsBeforeExecution));

        boolean executionPotentiallyChangedOutputs = false;
        try {
            executionPotentiallyChangedOutputs = executeInternal(work, inputSnapshots, inputFileFingerprints, hasEmptyPrimaryInputs.get() == Boolean.TRUE, outputFileFingerprintsBeforeExecution);
        } finally {
            if (executionPotentiallyChangedOutputs) {
                // TODO:lptr invalidate file system cache
                Map<String, FileCollectionFingerprint> outputFileFingerprintsAfterExecution = Maps.newLinkedHashMap();
                work.visitOutputs(new OutputFingerprinter(outputFingerprinter, outputFileFingerprintsAfterExecution));
            }
        }

        return null;
    }

    private boolean executeInternal(UnitOfWork work, Map<String, ValueSnapshot> inputSnapshots, Map<String, FileCollectionFingerprint> inputFileFingerprints, boolean hasEmptyPrimaryInputs, Map<String, FileCollectionFingerprint> outputFileFingerprintsBeforeExecution) {
        // TODO:lptr update task state
        if (hasEmptyPrimaryInputs) {
            final MutableBoolean removedAnything = new MutableBoolean();
            work.visitOutputs(new UnitOfWork.OutputVisitor() {
                @Override
                public void visitOutput(OutputFileProperty output) {
                    if (output.clean()) {
                        removedAnything.set(true);
                    }
                }
            });
            return removedAnything.get();
        }
    }

    private static class OutputFingerprinter implements UnitOfWork.OutputVisitor {
        private final FileCollectionFingerprinter outputFingerprinter;
        private final Map<String, FileCollectionFingerprint> outputFileFingerprintsBeforeExecution;

        public OutputFingerprinter(FileCollectionFingerprinter outputFingerprinter, Map<String, FileCollectionFingerprint> outputFileFingerprintsBeforeExecution) {
            this.outputFingerprinter = outputFingerprinter;
            this.outputFileFingerprintsBeforeExecution = outputFileFingerprintsBeforeExecution;
        }

        @Override
        public void visitOutput(OutputFileProperty output) {
            CurrentFileCollectionFingerprint fingerprint = outputFingerprinter.fingerprint(output.getFiles());
            outputFileFingerprintsBeforeExecution.put(output.getName(), fingerprint);
        }
    }
}
