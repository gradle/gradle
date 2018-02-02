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
import org.gradle.api.Transformer;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.tasks.WorkResult;
import org.gradle.internal.FileUtils;
import org.gradle.internal.IoActions;
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
import org.gradle.util.GFileUtils;
import org.gradle.util.VersionNumber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.JavaBeanDumper;
import org.yaml.snakeyaml.Loader;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// TODO(daniel): Swift compiler should extends from an abstraction of NativeCompiler (most of it applies to SwiftCompiler)
class SwiftCompiler extends AbstractCompiler<SwiftCompileSpec> {
    private static final Logger LOGGER = LoggerFactory.getLogger(SwiftCompiler.class);

    private final CompilerOutputFileNamingSchemeFactory compilerOutputFileNamingSchemeFactory;
    private final String objectFileExtension;
    private final VersionNumber swiftCompilerVersion;

    SwiftCompiler(BuildOperationExecutor buildOperationExecutor, CompilerOutputFileNamingSchemeFactory compilerOutputFileNamingSchemeFactory, CommandLineToolInvocationWorker commandLineToolInvocationWorker, CommandLineToolContext invocationContext, String objectFileExtension, WorkerLeaseService workerLeaseService, VersionNumber swiftCompilerVersion) {
        super(buildOperationExecutor, commandLineToolInvocationWorker, invocationContext, new SwiftCompileArgsTransformer(), false, workerLeaseService);
        this.compilerOutputFileNamingSchemeFactory = compilerOutputFileNamingSchemeFactory;
        this.objectFileExtension = objectFileExtension;
        this.swiftCompilerVersion = swiftCompilerVersion;
    }

    @Override
    protected void addOptionsFileArgs(List<String> args, File tempDir) {
    }

    @Override
    public WorkResult execute(SwiftCompileSpec spec) {
        if (swiftCompilerVersion.getMajor() < spec.getSourceCompatibility().getVersion()) {
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

                boolean canSafelyCompileIncrementally = adjustSwiftDepsForIncrementalCompile(moduleSwiftDeps, spec.getChangedFiles());

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

                genericArgs.add("-v");

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

                genericArgs.add("-swift-version");
                genericArgs.add(String.valueOf(spec.getSourceCompatibility().getVersion()));

                CommandLineToolInvocation perFileInvocation =
                    newInvocation("compiling swift file(s)", objectDir, Iterables.concat(genericArgs, outputArgs, importRootArgs), spec.getOperationLogger());
                perFileInvocation.getEnvironment().put("TMPDIR", spec.getTempDir().getAbsolutePath());
                buildQueue.add(perFileInvocation);
            }
        };
    }

    /*
version: "Swift version 4.0.3 (swift-4.0.3-RELEASE)"
options: "7890c730e32273cd2686f36d1bd976c0"
build_time: [1517422583, 339630833]
inputs:
  "fully-qualified-path/src/test/swift/BarTestSuite.swift": [9223372036, 854775807]
  "fully-qualified-path/src/test/swift/main.swift": [1517422583, 0]
  "fully-qualified-path/src/test/swift/FooTestSuite.swift": [1517422583, 0]
     */
    //CHECKSTYLE:OFF
    public static class SwiftDeps {
        private String version;
        private String options;
        private Integer[] build_time;
        private Map<String, Integer[]> inputs;

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public String getOptions() {
            return options;
        }

        public void setOptions(String options) {
            this.options = options;
        }

        public Integer[] getBuild_time() {
            return build_time;
        }

        public void setBuild_time(Integer[] build_time) {
            this.build_time = build_time;
        }

        public Map<String, Integer[]> getInputs() {
            return inputs;
        }

        public void setInputs(Map<String, Integer[]> inputs) {
            this.inputs = inputs;
        }
    }
    //CHECKSTYLE:ON

    /**
     * The peculiars of the swiftc incremental compiler can be extracted from the Driver's source code:
     * https://github.com/apple/swift/tree/d139ab29681d679337245f399dd8c76d620aa1aa/lib/Driver
     * And docs:
     * https://github.com/apple/swift/blob/d139ab29681d679337245f399dd8c76d620aa1aa/docs/Driver.md
     *
     * The incremental compiler uses the timestamp of source files and the timestamp in module.swiftdeps to
     * determine which files should be considered for compilation initially.  The compiler then looks at the
     * individual object's .swiftdeps file to build a dependency graph between changed and unchanged files.
     *
     * The incremental compiler will rebuild everything when:
     * - A source file is removed
     * - A different version of swiftc is used
     * - Different compiler arguments are used
     *
     * We work around issues with timestamps by changing module.swiftdeps and setting any changed files to
     * a timestamp of 0.  swiftc then sees those source files as different from the last compilation.
     *
     * If we have any issues reading or writing the swiftdeps file, we bail out and disable incremental compilation.
     */
    private boolean adjustSwiftDepsForIncrementalCompile(File moduleSwiftDeps, Collection<File> changedSources) {
        if (moduleSwiftDeps.exists()) {
            try {
                // Parse the existing module.swiftdeps and rewrite inputs with known changes
                final SwiftDeps swiftDeps = IoActions.withResource(new FileInputStream(moduleSwiftDeps), new Transformer<SwiftDeps, FileInputStream>() {
                    @Override
                    public SwiftDeps transform(FileInputStream fileInputStream) {
                        Yaml yaml = new Yaml(new Loader(new Constructor(SwiftDeps.class)));
                        return (SwiftDeps) yaml.load(fileInputStream);
                    }
                });
                // Update any previously known files with a bogus timestamp to force a rebuild
                Integer[] noTimestamp = {0, 0};
                for (File changedSource : changedSources) {
                    if (swiftDeps.inputs.containsKey(changedSource.getAbsolutePath())) {
                        swiftDeps.inputs.put(changedSource.getAbsolutePath(), noTimestamp);
                    }
                }
                // Rewrite the yaml file
                IoActions.writeTextFile(moduleSwiftDeps, new Action<BufferedWriter>() {
                    @Override
                    public void execute(BufferedWriter bufferedWriter) {
                        JavaBeanDumper yaml = new JavaBeanDumper(false);
                        yaml.dump(swiftDeps, bufferedWriter);
                        if (LOGGER.isDebugEnabled()) {
                            StringWriter sw = new StringWriter();
                            yaml.dump(swiftDeps, sw);
                            LOGGER.debug(sw.toString());
                        }
                    }
                });
            } catch (Exception e) {
                LOGGER.debug("could not update module.swiftdeps", e);
                return false;
            }
        }
        return true;
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
