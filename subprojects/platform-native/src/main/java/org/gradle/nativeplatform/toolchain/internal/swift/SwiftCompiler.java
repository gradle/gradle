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

package org.gradle.nativeplatform.toolchain.internal.swift;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.gradle.api.Action;
import org.gradle.api.Transformer;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.tasks.WorkResult;
import org.gradle.internal.FileUtils;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.BuildOperationQueue;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.internal.work.WorkerLeaseService;
import org.gradle.language.swift.SwiftVersion;
import org.gradle.nativeplatform.internal.CompilerOutputFileNamingSchemeFactory;
import org.gradle.nativeplatform.toolchain.internal.AbstractCompiler;
import org.gradle.nativeplatform.toolchain.internal.ArgsTransformer;
import org.gradle.nativeplatform.toolchain.internal.CommandLineToolContext;
import org.gradle.nativeplatform.toolchain.internal.CommandLineToolInvocation;
import org.gradle.nativeplatform.toolchain.internal.CommandLineToolInvocationWorker;
import org.gradle.nativeplatform.toolchain.internal.compilespec.SwiftCompileSpec;
import org.gradle.util.CollectionUtils;
import org.gradle.util.GFileUtils;
import org.gradle.util.VersionNumber;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// TODO(daniel): Swift compiler should extends from an abstraction of NativeCompiler (most of it applies to SwiftCompiler)
class SwiftCompiler extends AbstractCompiler<SwiftCompileSpec> {

    private final CompilerOutputFileNamingSchemeFactory compilerOutputFileNamingSchemeFactory;
    private final String objectFileExtension;
    private final VersionNumber swiftCompilerVersion;
    private final SwiftDepsHandler swiftDepsHandler;

    SwiftCompiler(BuildOperationExecutor buildOperationExecutor, CompilerOutputFileNamingSchemeFactory compilerOutputFileNamingSchemeFactory, CommandLineToolInvocationWorker commandLineToolInvocationWorker, CommandLineToolContext invocationContext, String objectFileExtension, WorkerLeaseService workerLeaseService, VersionNumber swiftCompilerVersion) {
        super(buildOperationExecutor, commandLineToolInvocationWorker, invocationContext, new SwiftCompileArgsTransformer(), false, workerLeaseService);
        this.compilerOutputFileNamingSchemeFactory = compilerOutputFileNamingSchemeFactory;
        this.objectFileExtension = objectFileExtension;
        this.swiftCompilerVersion = swiftCompilerVersion;
        this.swiftDepsHandler = new SwiftDepsHandler();
    }

    @Override
    protected void addOptionsFileArgs(List<String> args, File tempDir) {
    }

    @Override
    public WorkResult execute(SwiftCompileSpec spec) {
        if (swiftCompilerVersion.getMajor() < spec.getSourceCompatibility().getVersion() || (swiftCompilerVersion.getMajor() >= 5 && spec.getSourceCompatibility().equals(SwiftVersion.SWIFT3))) {
            throw new IllegalArgumentException(String.format("Swift compiler version '%s' doesn't support Swift language version '%d'", swiftCompilerVersion.toString(), spec.getSourceCompatibility().getVersion()));
        }
        return super.execute(spec);
    }

    protected File getOutputFileDir(File sourceFile, File objectFileDir, String fileSuffix) {
        boolean windowsPathLimitation = OperatingSystem.current().isWindows();

        File outputFile = compilerOutputFileNamingSchemeFactory.create()
            .withObjectFileNameSuffix(fileSuffix)
            .withOutputBaseFolder(objectFileDir)
            .map(sourceFile);
        File outputDirectory = outputFile.getParentFile();
        GFileUtils.mkdirs(outputDirectory);
        return windowsPathLimitation ? FileUtils.assertInWindowsPathLengthLimitation(outputFile) : outputFile;
    }

    @Override
    protected Action<BuildOperationQueue<CommandLineToolInvocation>> newInvocationAction(final SwiftCompileSpec spec, final List<String> genericArgs) {
        final File objectDir = spec.getObjectFileDir();
        return new Action<BuildOperationQueue<CommandLineToolInvocation>>() {
            @Override
            public void execute(BuildOperationQueue<CommandLineToolInvocation> buildQueue) {
                buildQueue.setLogLocation(spec.getOperationLogger().getLogLocation());

                OutputFileMap outputFileMap = new OutputFileMap();

                File moduleSwiftDeps = new File(objectDir, "module.swiftdeps");
                outputFileMap.root().swiftDependenciesFile(moduleSwiftDeps);

                for (File sourceFile : spec.getSourceFiles()) {
                    outputFileMap.newEntry(sourceFile.getAbsolutePath())
                        .dependencyFile(getOutputFileDir(sourceFile, objectDir, ".d"))
                        .diagnosticsFile(getOutputFileDir(sourceFile, objectDir, ".dia"))
                        .objectFile(getOutputFileDir(sourceFile, objectDir, objectFileExtension))
                        .swiftModuleFile(getOutputFileDir(sourceFile, objectDir, "~partial.swiftmodule"))
                        .swiftDependenciesFile(getOutputFileDir(sourceFile, objectDir, ".swiftdeps"));
                    genericArgs.add(sourceFile.getAbsolutePath());
                }
                if (null != spec.getModuleName()) {
                    genericArgs.add("-module-name");
                    genericArgs.add(spec.getModuleName());
                    genericArgs.add("-emit-module-path");
                    genericArgs.add(spec.getModuleFile().getAbsolutePath());
                }


                boolean canSafelyCompileIncrementally = swiftDepsHandler.adjustTimestampsFor(moduleSwiftDeps, spec.getChangedFiles());
                if (canSafelyCompileIncrementally) {
                    genericArgs.add("-incremental");
                    genericArgs.add("-emit-dependencies");
                }

                genericArgs.add("-emit-object");

                File outputFileMapFile = new File(spec.getObjectFileDir(), "output-file-map.json");
                outputFileMap.writeToFile(outputFileMapFile);

                List<String> outputArgs = Lists.newArrayList();
                outputArgs.add("-output-file-map");
                outputArgs.add(outputFileMapFile.getAbsolutePath());

                List<String> importRootArgs = Lists.newArrayList();
                for (File importRoot : spec.getIncludeRoots()) {
                    importRootArgs.add("-I");
                    importRootArgs.add(importRoot.getAbsolutePath());
                }
                if (spec.isDebuggable()) {
                    genericArgs.add("-g");
                }
                if (spec.isOptimized()) {
                    genericArgs.add("-O");
                }

                genericArgs.addAll(CollectionUtils.collect(spec.getMacros().keySet(), new Transformer<String, String>() {
                    @Override
                    public String transform(String macro) {
                        return "-D" + macro;
                    }
                }));

                genericArgs.add("-swift-version");
                genericArgs.add(String.valueOf(spec.getSourceCompatibility().getVersion()));

                CommandLineToolInvocation perFileInvocation =
                    newInvocation("compiling swift file(s)", objectDir, Iterables.concat(genericArgs, outputArgs, importRootArgs), spec.getOperationLogger());
                perFileInvocation.getEnvironment().put("TMPDIR", spec.getTempDir().getAbsolutePath());
                buildQueue.add(perFileInvocation);
            }
        };
    }

    private static class SwiftCompileArgsTransformer implements ArgsTransformer<SwiftCompileSpec> {
        @Override
        public List<String> transform(SwiftCompileSpec swiftCompileSpec) {
            return swiftCompileSpec.getArgs();
        }
    }

    private static class OutputFileMap {
        private Map<String, Entry> entries = new HashMap<String, Entry>();

        public Builder root() {
            return newEntry("");
        }

        public Builder newEntry(String name) {
            Entry entry = new Entry();
            entries.put(name, entry);

            return new Builder(entry);
        }

        private void toJson(Appendable writer) {
            Gson gson = new GsonBuilder()
                .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_DASHES)
                .setPrettyPrinting()
                .create();
            gson.toJson(entries, writer);
        }

        public void writeToFile(File outputFile) {
            try (Writer writer = new PrintWriter(outputFile)) {
                toJson(writer);
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }

        private static class Builder {
            private final Entry entry;
            Builder(Entry entry) {
                this.entry = entry;
            }

            public Builder dependencyFile(File dependencyFile) {
                entry.dependencies = dependencyFile.getAbsolutePath();
                return this;
            }

            public Builder objectFile(File objectFile) {
                entry.object = objectFile.getAbsolutePath();
                return this;
            }

            public Builder swiftModuleFile(File swiftModuleFile) {
                entry.swiftmodule = swiftModuleFile.getAbsolutePath();
                return this;
            }

            public Builder swiftDependenciesFile(File swiftDependenciesFile) {
                entry.swiftDependencies = swiftDependenciesFile.getAbsolutePath();
                return this;
            }

            public Builder diagnosticsFile(File diagnosticsFile) {
                entry.diagnostics = diagnosticsFile.getAbsolutePath();
                return this;
            }
        }

        private static class Entry {
            private String dependencies;
            private String object;
            private String swiftmodule;
            private String swiftDependencies;
            private String diagnostics;
        }
    }
}
