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
import org.apache.commons.io.IOUtils;
import org.gradle.api.Action;
import org.gradle.api.UncheckedIOException;
import org.gradle.internal.FileUtils;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.BuildOperationQueue;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.internal.work.WorkerLeaseService;
import org.gradle.nativeplatform.internal.CompilerOutputFileNamingSchemeFactory;
import org.gradle.nativeplatform.toolchain.internal.AbstractCompiler;
import org.gradle.nativeplatform.toolchain.internal.ArgsTransformer;
import org.gradle.nativeplatform.toolchain.internal.CommandLineToolContext;
import org.gradle.nativeplatform.toolchain.internal.CommandLineToolInvocation;
import org.gradle.nativeplatform.toolchain.internal.CommandLineToolInvocationWorker;
import org.gradle.nativeplatform.toolchain.internal.compilespec.SwiftCompileSpec;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// TODO(daniel): Swift compiler should extends from an abstraction of NativeCompiler (most of is applies to SwiftCompiler)
class SwiftCompiler extends AbstractCompiler<SwiftCompileSpec> {
    private final CompilerOutputFileNamingSchemeFactory compilerOutputFileNamingSchemeFactory;
    private final String objectFileExtension;

    SwiftCompiler(BuildOperationExecutor buildOperationExecutor, CompilerOutputFileNamingSchemeFactory compilerOutputFileNamingSchemeFactory, CommandLineToolInvocationWorker commandLineToolInvocationWorker, CommandLineToolContext invocationContext, String objectFileExtension, WorkerLeaseService workerLeaseService) {
        super(buildOperationExecutor, commandLineToolInvocationWorker, invocationContext, new SwiftCompileArgsTransformer(), false, workerLeaseService);
        this.compilerOutputFileNamingSchemeFactory = compilerOutputFileNamingSchemeFactory;
        this.objectFileExtension = objectFileExtension;
    }

    @Override
    protected void addOptionsFileArgs(List<String> args, File tempDir) {
    }

    protected File getOutputFileDir(File sourceFile, File objectFileDir, String fileSuffix) {
        boolean windowsPathLimitation = OperatingSystem.current().isWindows();

        File outputFile = compilerOutputFileNamingSchemeFactory.create()
            .withObjectFileNameSuffix(fileSuffix)
            .withOutputBaseFolder(objectFileDir)
            .map(sourceFile);
        File outputDirectory = outputFile.getParentFile();
        if (!outputDirectory.exists()) {
            outputDirectory.mkdirs();
        }
        return windowsPathLimitation ? FileUtils.assertInWindowsPathLengthLimitation(outputFile) : outputFile;
    }

    @Override
    protected Action<BuildOperationQueue<CommandLineToolInvocation>> newInvocationAction(final SwiftCompileSpec spec) {
        final List<String> genericArgs = getArguments(spec);

        final File objectDir = spec.getObjectFileDir();
        return new Action<BuildOperationQueue<CommandLineToolInvocation>>() {
            @Override
            public void execute(BuildOperationQueue<CommandLineToolInvocation> buildQueue) {
                buildQueue.setLogLocation(spec.getOperationLogger().getLogLocation());

                OutputFileMap outputFileMap = new OutputFileMap();
                for (File sourceFile : spec.getSourceFiles()) {
                    outputFileMap.newEntry(sourceFile.getAbsolutePath())
                        .dependencyFile(getOutputFileDir(sourceFile, objectDir, ".d"))
                        .objectFile(getOutputFileDir(sourceFile, objectDir, objectFileExtension))
                        .swiftModuleFile(getOutputFileDir(sourceFile, objectDir, "~partial.swiftmodule"))
                        .swiftDependenciesFile(getOutputFileDir(sourceFile, objectDir, ".swiftdeps"))
                        .diagnosticsFile(getOutputFileDir(sourceFile, objectDir, ".dia"));
                    genericArgs.add(sourceFile.getAbsolutePath());
                }
                if (null != spec.getModuleName()) {
                    genericArgs.add("-emit-module");
                    genericArgs.add("-module-name");
                    genericArgs.add(spec.getModuleName());
                    outputFileMap.newEntry("")
                        .swiftModuleFile(new File(spec.getObjectFileDir(), spec.getModuleName() + ".swiftmodule"));
                }
                genericArgs.add("-v");
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

                CommandLineToolInvocation perFileInvocation =
                    newInvocation("compiling swift file(s)", objectDir, Iterables.concat(genericArgs, outputArgs, importRootArgs), spec.getOperationLogger());
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
            try {
                Writer writer = new PrintWriter(outputFile);
                try {
                    toJson(writer);
                } finally {
                    IOUtils.closeQuietly(writer);
                }
            } catch (FileNotFoundException ex) {
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
