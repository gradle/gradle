/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.internal.initialization.transform;

import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.internal.classpath.TransformedClassPath;
import org.gradle.internal.execution.ImmutableUnitOfWork;
import org.gradle.internal.execution.InputFingerprinter;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.caching.CachingDisabledReason;
import org.gradle.internal.execution.caching.CachingDisabledReasonCategory;
import org.gradle.internal.execution.history.OverlappingOutputs;
import org.gradle.internal.execution.model.InputNormalizer;
import org.gradle.internal.execution.workspace.ImmutableWorkspaceProvider;
import org.gradle.internal.file.TreeType;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.DirectorySensitivity;
import org.gradle.internal.fingerprint.LineEndingSensitivity;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.hash.Hashing;
import org.gradle.internal.properties.InputBehavior;
import org.gradle.internal.serialize.kryo.KryoBackedDecoder;
import org.gradle.internal.serialize.kryo.KryoBackedEncoder;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.util.internal.GFileUtils;

import javax.annotation.Nullable;
import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

public abstract class CoarseGrainedInstrumentationUnitOfWork implements ImmutableUnitOfWork {

    private static final CachingDisabledReason NOT_CACHEABLE = new CachingDisabledReason(CachingDisabledReasonCategory.NOT_CACHEABLE, "Not worth caching.");
    private final InputFingerprinter inputFingerprinter;
    private final FileCollection classpathConfiguration;
    private final FileCollectionFactory fileCollectionFactory;
    private final ImmutableWorkspaceProvider workspaceProvider;

    public CoarseGrainedInstrumentationUnitOfWork(
        InputFingerprinter inputFingerprinter,
        FileCollectionFactory fileCollectionFactory,
        ImmutableWorkspaceProvider workspaceProvider,
        FileCollection classpathConfiguration
    ) {
        this.inputFingerprinter = inputFingerprinter;
        this.fileCollectionFactory = fileCollectionFactory;
        this.workspaceProvider = workspaceProvider;
        this.classpathConfiguration = classpathConfiguration;
    }

    @Override
    public Optional<CachingDisabledReason> shouldDisableCaching(@Nullable OverlappingOutputs detectedOverlappingOutputs) {
        return Optional.of(NOT_CACHEABLE);
    }

    @Override
    public ImmutableWorkspaceProvider getWorkspaceProvider() {
        return workspaceProvider;
    }

    @Override
    public void visitIdentityInputs(InputVisitor visitor) {
        visitor.visitInputFileProperty("classpath", InputBehavior.NON_INCREMENTAL, new InputFileValueSupplier(
            classpathConfiguration,
            InputNormalizer.RUNTIME_CLASSPATH,
            DirectorySensitivity.DEFAULT,
            LineEndingSensitivity.DEFAULT,
            () -> classpathConfiguration
        ));
    }

    @Override
    public Identity identify(Map<String, ValueSnapshot> identityInputs, Map<String, CurrentFileCollectionFingerprint> identityFileInputs) {
        Hasher hasher = Hashing.newHasher();
        identityInputs.values().forEach(value -> requireNonNull(value).appendToHasher(hasher));
        identityFileInputs.values().forEach(value -> hasher.putHash(value.getHash()));
        String identity = hasher.hash().toString();
        return () -> identity;
    }

    public abstract TransformedClassPath getInstrumentedClasspath();

    @Override
    public WorkOutput execute(ExecutionRequest executionRequest) {
        TransformedClassPath classPath = getInstrumentedClasspath();
        writeInstrumentedClasspath(executionRequest.getWorkspace(), classPath);
        return new WorkOutput() {
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
        return readInstrumentedClasspath(workspace);
    }

    @Override
    public InputFingerprinter getInputFingerprinter() {
        return inputFingerprinter;
    }

    @Override
    public void visitOutputs(File workspace, OutputVisitor visitor) {
        TransformedClassPath classPath = readInstrumentedClasspath(workspace);
        File classpathFile = new File(workspace, "instrumented-classpath.bin");
        OutputFileValueSupplier classpath = OutputFileValueSupplier.fromStatic(classpathFile, fileCollectionFactory.fixed(classpathFile));
        visitor.visitOutputProperty("classpath", TreeType.FILE, classpath);
        if (classPath == null) {
            return;
        }
        Set<File> originalFiles = classpathConfiguration.getFiles();
        AtomicInteger i = new AtomicInteger();
        Stream.concat(classPath.getAsFiles().stream(), classPath.getAsTransformedFiles().stream())
            .filter(file -> !originalFiles.contains(file))
            .forEach(file -> {
                if (file.isDirectory()) {
                    OutputFileValueSupplier instrumentedOutputValue = OutputFileValueSupplier.fromStatic(file, fileCollectionFactory.fixed(file));
                    visitor.visitOutputProperty("a" + i.incrementAndGet(), TreeType.DIRECTORY, instrumentedOutputValue);
                } else {
                    OutputFileValueSupplier instrumentedOutputValue = OutputFileValueSupplier.fromStatic(file, fileCollectionFactory.fixed(file));
                    visitor.visitOutputProperty("a" + i.incrementAndGet(), TreeType.FILE, instrumentedOutputValue);
                }
            });
    }

    @Override
    public String getDisplayName() {
        return "Build script classpath instrumentation";
    }

    private void writeInstrumentedClasspath(File workspace, TransformedClassPath classPath) {
        Map<File, Integer> fileToIndex = new HashMap<>();
        int i = 0;
        for (File f : classpathConfiguration.getFiles()) {
            fileToIndex.put(f, i++);
        }
        GFileUtils.mkdirs(workspace);
        File output = new File(workspace, "instrumented-classpath.bin");
        try (KryoBackedEncoder encoder = new KryoBackedEncoder(Files.newOutputStream(output.toPath()))) {
            List<File> originalClasspath = classPath.getAsFiles();
            encoder.writeInt(originalClasspath.size());
            for (File file : originalClasspath) {
                File transformed = classPath.findTransformedEntryFor(file);
                if (fileToIndex.containsKey(file)) {
                    encoder.writeBoolean(true);
                    encoder.writeInt(fileToIndex.get(file));
                } else {
                    encoder.writeBoolean(false);
                    encoder.writeString(file.getAbsolutePath());
                }
                encoder.writeString(transformed.getAbsolutePath());
            }
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialize types map to a file: " + output, e);
        }
    }

    @Nullable
    private TransformedClassPath readInstrumentedClasspath(File workspace) {
        Map<Integer, File> fileToIndex = new HashMap<>();
        int i = 0;
        for (File f : classpathConfiguration.getFiles()) {
            fileToIndex.put(i++, f);
        }
        TransformedClassPath.Builder classPath;
        File input = new File(workspace, "instrumented-classpath.bin");
        if (!input.exists()) {
            return null;
        }
        try (KryoBackedDecoder decoder = new KryoBackedDecoder(Files.newInputStream(input.toPath()))) {
            int files = decoder.readInt();
            classPath = TransformedClassPath.builderWithExactSize(files);
            for (int j = 0; j < files; j++) {
                boolean isIndex = decoder.readBoolean();
                int index = isIndex ? decoder.readInt() : -1;
                File original = isIndex ? fileToIndex.get(index) : new File(decoder.readString());
                File transformed = new File(decoder.readString());
                classPath.add(original, transformed);
            }
            return classPath.build();
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialize types map to a file");
        }
    }
}
