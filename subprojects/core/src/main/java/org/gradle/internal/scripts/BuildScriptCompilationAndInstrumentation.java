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
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.classpath.transforms.ClassTransforms;
import org.gradle.internal.classpath.transforms.ClasspathElementTransformFactoryForLegacy;
import org.gradle.internal.classpath.transforms.InstrumentingClassTransform;
import org.gradle.internal.classpath.types.GradleCoreInstrumentationTypeRegistry;
import org.gradle.internal.classpath.types.InstrumentationTypeRegistry;
import org.gradle.internal.execution.ExecutionContext;
import org.gradle.internal.execution.Identity;
import org.gradle.internal.execution.ImmutableUnitOfWork;
import org.gradle.internal.execution.InputFingerprinter;
import org.gradle.internal.execution.InputVisitor;
import org.gradle.internal.execution.OutputVisitor;
import org.gradle.internal.execution.WorkOutput;
import org.gradle.internal.execution.caching.CachingDisabledReason;
import org.gradle.internal.execution.caching.CachingDisabledReasonCategory;
import org.gradle.internal.execution.history.OverlappingOutputs;
import org.gradle.internal.execution.workspace.ImmutableWorkspaceProvider;
import org.gradle.internal.file.TreeType;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.hash.Hashing;
import org.gradle.internal.instrumentation.reporting.PropertyUpgradeReportConfig;
import org.gradle.internal.instrumentation.reporting.listener.BytecodeUpgradeReportMethodInterceptionListener;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.util.internal.GFileUtils;
import org.jspecify.annotations.Nullable;

import javax.annotation.OverridingMethodsMustInvokeSuper;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static java.util.Objects.requireNonNull;
import static org.gradle.internal.instrumentation.api.types.BytecodeInterceptorFilter.INSTRUMENTATION_AND_BYTECODE_REPORT;
import static org.gradle.internal.instrumentation.api.types.BytecodeInterceptorFilter.INSTRUMENTATION_ONLY;
import static org.gradle.internal.instrumentation.reporting.MethodInterceptionReportCollector.INTERCEPTED_METHODS_REPORT_FILE;

/**
 * A base class that represents a work for compilation for Kotlin and Groovy build scripts.
 * This work unit first compiles the build script to a directory, and then instruments the directory for configuration cache and returns instrumented output.
 */
public abstract class BuildScriptCompilationAndInstrumentation implements ImmutableUnitOfWork {

    private static final CachingDisabledReason CACHING_DISABLED_FOR_PROPERTY_REPORT = new CachingDisabledReason(CachingDisabledReasonCategory.NOT_CACHEABLE, "Caching of buildscript compilation disabled due for property upgrade report");

    private final ScriptSource source;
    private final ImmutableWorkspaceProvider workspaceProvider;
    private final InputFingerprinter inputFingerprinter;
    private final ClasspathElementTransformFactoryForLegacy transformFactory;
    protected final FileCollectionFactory fileCollectionFactory;
    private final GradleCoreInstrumentationTypeRegistry gradleCoreTypeRegistry;
    private final PropertyUpgradeReportConfig propertyUpgradeReportConfig;

    public BuildScriptCompilationAndInstrumentation(
        ScriptSource source,
        ImmutableWorkspaceProvider workspaceProvider,
        FileCollectionFactory fileCollectionFactory,
        InputFingerprinter inputFingerprinter,
        ClasspathElementTransformFactoryForLegacy transformFactory,
        GradleCoreInstrumentationTypeRegistry gradleCoreTypeRegistry,
        PropertyUpgradeReportConfig propertyUpgradeReportConfig
    ) {
        this.source = source;
        this.workspaceProvider = workspaceProvider;
        this.fileCollectionFactory = fileCollectionFactory;
        this.inputFingerprinter = inputFingerprinter;
        this.transformFactory = transformFactory;
        this.gradleCoreTypeRegistry = gradleCoreTypeRegistry;
        this.propertyUpgradeReportConfig = propertyUpgradeReportConfig;
    }

    @Override
    public Optional<CachingDisabledReason> shouldDisableCaching(@Nullable OverlappingOutputs detectedOverlappingOutputs) {
        // Disable caching always for property upgrade report,
        // since there is not much use to cache report remotely, also report can contain absolute paths
        return propertyUpgradeReportConfig.isEnabled() ? Optional.of(CACHING_DISABLED_FOR_PROPERTY_REPORT) : Optional.empty();
    }

    @Override
    @OverridingMethodsMustInvokeSuper
    public void visitImmutableInputs(InputVisitor visitor) {
        visitor.visitInputProperty("isProviderUpgradeReportEnabled", propertyUpgradeReportConfig::isEnabled);
    }

    /**
     * A compile operation. It should return a File where classes are compiled to.
     */
    protected abstract File compile(File workspace);

    /**
     * Provides a File where instrumented output will be written to.
     */
    protected abstract File instrumentedOutput(File workspace);

    /**
     * The type of the instrumented output: a directory by default.
     */
    protected TreeType instrumentedOutputType() {
        return TreeType.DIRECTORY;
    }

    /**
     * Compiles the script and writes the instrumented classes to {@link #instrumentedOutput(File)}.
     * By default, compiles to {@link #compile(File)} and instruments the result.
     */
    protected void compileAndInstrument(File workspace) {
        File compileOutput = compile(workspace);
        withClassTransform(propertyUpgradeReport(workspace), classTransform ->
            transformFactory.createTransformer(compileOutput, classTransform).transform(instrumentedOutput(workspace))
        );
    }

    /**
     * Instruments classes that were compiled into memory and writes them to a single jar.
     *
     * @param classes class bytes by internal class name
     */
    protected void instrumentToJar(Map<String, byte[]> classes, File destinationJar, File propertyUpgradeReport) {
        withClassTransform(propertyUpgradeReport, classTransform -> {
            GFileUtils.mkdirs(destinationJar.getParentFile());
            try (ZipOutputStream jar = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(destinationJar)))) {
                // The jar is only ever read from the local file system, compressing it is not worth the time
                jar.setLevel(Deflater.NO_COMPRESSION);
                for (Map.Entry<String, byte[]> entry : classes.entrySet()) {
                    jar.putNextEntry(new ZipEntry(entry.getKey() + ".class"));
                    jar.write(ClassTransforms.applyToBytes(classTransform, entry.getKey(), entry.getValue()));
                    jar.closeEntry();
                }
            } catch (IOException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        });
    }

    private void withClassTransform(File propertyUpgradeReport, Consumer<InstrumentingClassTransform> action) {
        if (propertyUpgradeReportConfig.isEnabled()) {
            File source = this.source.getResource().getFile();
            try (BytecodeUpgradeReportMethodInterceptionListener methodInterceptionListener = new BytecodeUpgradeReportMethodInterceptionListener(source, propertyUpgradeReport)) {
                // TODO: Using gradleCoreTypeRegistry means we won't detect user types that extend from Gradle types, fix that
                action.accept(new InstrumentingClassTransform(INSTRUMENTATION_AND_BYTECODE_REPORT, gradleCoreTypeRegistry, methodInterceptionListener));
            }
        } else {
            action.accept(new InstrumentingClassTransform(INSTRUMENTATION_ONLY, InstrumentationTypeRegistry.EMPTY));
        }
    }

    protected File propertyUpgradeReport(File workspace) {
        return new File(workspace, "reports/" + INTERCEPTED_METHODS_REPORT_FILE);
    }

    @Override
    public Identity identify(Map<String, ValueSnapshot> scalarInputs, Map<String, CurrentFileCollectionFingerprint> fileInputs) {
        Hasher hasher = Hashing.newHasher();
        scalarInputs.values().forEach(value -> requireNonNull(value).appendToHasher(hasher));
        String identity = hasher.hash().toString();
        return () -> identity;
    }

    @Override
    @OverridingMethodsMustInvokeSuper
    public void visitOutputs(File workspace, OutputVisitor visitor) {
        File instrumentedOutput = instrumentedOutput(workspace);
        OutputVisitor.OutputFileValueSupplier instrumentedOutputValue = OutputVisitor.OutputFileValueSupplier.fromStatic(instrumentedOutput, fileCollectionFactory.fixed(instrumentedOutput));
        visitor.visitOutputProperty("instrumentedOutput", instrumentedOutputType(), instrumentedOutputValue);

        File propertyUpgradeReport = propertyUpgradeReport(workspace);
        OutputVisitor.OutputFileValueSupplier propertyUpgradeReportOutputValue = OutputVisitor.OutputFileValueSupplier.fromStatic(propertyUpgradeReport, fileCollectionFactory.fixed(propertyUpgradeReport));
        visitor.visitOutputProperty("propertyUpgradeReportOutput", TreeType.FILE, propertyUpgradeReportOutputValue);
    }

    @Override
    public WorkOutput execute(ExecutionContext executionContext) {
        File workspace = executionContext.getWorkspace();
        compileAndInstrument(workspace);
        return new WorkOutput() {
            @Override
            public WorkResult getDidWork() {
                return WorkOutput.WorkResult.DID_WORK;
            }

            @Override
            public Object getOutput(File workspace) {
                return loadAlreadyProducedOutput(workspace);
            }
        };
    }

    @Override
    public Object loadAlreadyProducedOutput(File workspace) {
        return new Output(instrumentedOutput(workspace), propertyUpgradeReport(workspace));
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

    public static class Output {
        private final File instrumentedOutput;
        private final File propertyUpgradeReport;

        public Output(File instrumentedOutput, File propertyUpgradeReport) {
            this.instrumentedOutput = instrumentedOutput;
            this.propertyUpgradeReport = propertyUpgradeReport;
        }

        public File getInstrumentedOutput() {
            return instrumentedOutput;
        }

        public File getPropertyUpgradeReport() {
            return propertyUpgradeReport;
        }
    }
}
