/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.api.tasks.compile;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.JavaVersion;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.FileType;
import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.api.internal.tasks.JavaToolChainFactory;
import org.gradle.api.internal.tasks.compile.CleaningGroovyCompiler;
import org.gradle.api.internal.tasks.compile.CompilerForkUtils;
import org.gradle.api.internal.tasks.compile.DefaultGroovyJavaJointCompileSpec;
import org.gradle.api.internal.tasks.compile.DefaultGroovyJavaJointCompileSpecFactory;
import org.gradle.api.internal.tasks.compile.GroovyCompilerFactory;
import org.gradle.api.internal.tasks.compile.GroovyJavaJointCompileSpec;
import org.gradle.api.internal.tasks.compile.processing.AnnotationProcessorDetector;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.CompileClasspath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.LocalState;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.WorkResult;
import org.gradle.internal.IoActions;
import org.gradle.internal.jvm.inspection.JvmVersionDetector;
import org.gradle.jvm.toolchain.JavaToolChain;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.process.internal.JavaForkOptionsFactory;
import org.gradle.process.internal.worker.child.WorkerDirectoryProvider;
import org.gradle.util.GFileUtils;
import org.gradle.work.FileChange;
import org.gradle.work.InputChanges;
import org.gradle.workers.internal.IsolatedClassloaderWorkerFactory;
import org.gradle.workers.internal.WorkerDaemonFactory;

import javax.inject.Inject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Compiles Groovy source files, and optionally, Java source files.
 */
@CacheableTask
public class GroovyCompile extends AbstractCompile {
    private Compiler<GroovyJavaJointCompileSpec> compiler;
    private FileCollection groovyClasspath;
    private final CompileOptions compileOptions;
    private final GroovyCompileOptions groovyCompileOptions = new GroovyCompileOptions();
    private final ConfigurableFileCollection astTransformClasspath;
    private final File sourceClassesMappingFile;
    private final FileCollection stableSources = getProject().files(new Callable<FileTree>() {
        @Override
        public FileTree call() {
            return getSource();
        }
    });

    public GroovyCompile() {
        ObjectFactory objectFactory = getServices().get(ObjectFactory.class);
        CompileOptions compileOptions = objectFactory.newInstance(CompileOptions.class);
        compileOptions.setIncremental(false);
        this.compileOptions = compileOptions;
        this.astTransformClasspath = objectFactory.fileCollection();
        CompilerForkUtils.doNotCacheIfForkingViaExecutable(compileOptions, getOutputs());
        boolean enableCompileAvoidance = Boolean.getBoolean("org.gradle.unsafe.groovy.compile.avoidance");
        if (!enableCompileAvoidance) {
            astTransformClasspath.from(new Callable<FileCollection>() {
                @Override
                public FileCollection call() {
                    return getClasspath();
                }
            });
        }
        this.sourceClassesMappingFile = new File(getTemporaryDir(), "source-classes-mapping.txt");
    }

    @Override
    protected void compile() {
        checkGroovyClasspathIsNonEmpty();
        DefaultGroovyJavaJointCompileSpec spec = createSpec();
        WorkResult result = getCompiler(spec, false).execute(spec);
        setDidWork(result.getDidWork());
        updateMappingsFile(spec.getCompilationMappingFile(), ImmutableMultimap.<File, String>of());
    }

    @TaskAction
    protected void compile(InputChanges inputChanges) {
        System.out.println("Incremental compilation: " + inputChanges.isIncremental());
        if (!getOptions().isIncremental()) {
            compile();
            return;
        }

        Multimap<File, String> sourceClassesMapping = readSourceClassesMapping(sourceClassesMappingFile);
        if (!inputChanges.isIncremental() || !sourceClassesMappingFile.isFile()) {
            compile();
        } else {
            List<File> sourceFilesToCompile = new ArrayList<File>();
            for (FileChange fileChange : inputChanges.getFileChanges(getStableSources())) {
                if (fileChange.getFileType() == FileType.DIRECTORY) {
                    continue;
                }
                switch (fileChange.getChangeType()) {
                    case REMOVED:
                        deleteAffectedOutputs(fileChange.getFile(), sourceClassesMapping);
                        break;
                    case MODIFIED:
                        deleteAffectedOutputs(fileChange.getFile(), sourceClassesMapping);
                        sourceFilesToCompile.add(fileChange.getFile());
                        break;
                    case ADDED:
                        sourceFilesToCompile.add(fileChange.getFile());
                        break;
                }
            }
            DefaultGroovyJavaJointCompileSpec spec = createSpec(sourceFilesToCompile);
            WorkResult result = getCompiler(spec, true).execute(spec);
            setDidWork(result.getDidWork());
            updateMappingsFile(spec.getCompilationMappingFile(), sourceClassesMapping);
        }
    }

    private void updateMappingsFile(File compilationMappingFile, Multimap<File, String> oldMapping) {
        Multimap<File, String> mapping = MultimapBuilder.hashKeys().arrayListValues().build();
        mapping.putAll(oldMapping);
        if (compilationMappingFile != null && compilationMappingFile.isFile()) {
            Multimap<File, String> newSourceClassesMapping = readSourceClassesMapping(compilationMappingFile);
            mapping.putAll(newSourceClassesMapping);
        }
        PrintWriter fileWriter = null;
        try {
            fileWriter = new PrintWriter(new FileWriter(sourceClassesMappingFile));
            for (Map.Entry<File, String> entry : mapping.entries()) {
                fileWriter.println("file:" + entry.getKey());
                fileWriter.println(entry.getValue());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            IoActions.closeQuietly(fileWriter);
        }
    }

    private void deleteAffectedOutputs(File sourceFile, Multimap<File, String> sourceClassesMapping) {
        Collection<String> affectedFiles = sourceClassesMapping.get(sourceFile);
        if (affectedFiles.isEmpty()) {
            System.out.println("No previous output files recorded for source file: " + sourceFile);
        }
        for (String affectedClass : affectedFiles) {
            File classFile = new File(getDestinationDir(), affectedClass.replace(".", "/") + ".class");
            System.out.println("Deleting " + classFile);
            classFile.delete();
        }
        sourceClassesMapping.removeAll(sourceFile);
    }

    private Multimap<File, String> readSourceClassesMapping(File compilationMappingFile) {
        Multimap<File, String> sourceClassesMapping = MultimapBuilder.ListMultimapBuilder
            .hashKeys()
            .arrayListValues()
            .build();
        if (compilationMappingFile.isFile()) {
            BufferedReader reader = null;
            try {
                reader = new BufferedReader(new InputStreamReader(Files.newInputStream(compilationMappingFile.toPath())));
                for (String uri = reader.readLine(); uri != null; uri = reader.readLine()) {
                    String className = Preconditions.checkNotNull(reader.readLine());
                    if (uri.startsWith("file:")) {
                        sourceClassesMapping.put(new File(uri.substring(5)), className);
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } finally {
                IoActions.closeQuietly(reader);
            }
        }
        return sourceClassesMapping;
    }

    @SkipWhenEmpty
    @PathSensitive(PathSensitivity.RELATIVE) // Java source files are supported, too. Therefore we should care about the relative path.
    @InputFiles
    protected FileCollection getStableSources() {
        return stableSources;
    }

    @Optional
    @LocalState
    protected File getSourceClassesMappingFile() {
        return compileOptions.isIncremental() ? sourceClassesMappingFile : null;
    }

    private Compiler<GroovyJavaJointCompileSpec> getCompiler(GroovyJavaJointCompileSpec spec, boolean incremental) {
        if (compiler == null) {
            WorkerDaemonFactory workerDaemonFactory = getServices().get(WorkerDaemonFactory.class);
            IsolatedClassloaderWorkerFactory inProcessWorkerFactory = getServices().get(IsolatedClassloaderWorkerFactory.class);
            JavaForkOptionsFactory forkOptionsFactory = getServices().get(JavaForkOptionsFactory.class);
            AnnotationProcessorDetector processorDetector = getServices().get(AnnotationProcessorDetector.class);
            JvmVersionDetector jvmVersionDetector = getServices().get(JvmVersionDetector.class);
            WorkerDirectoryProvider workerDirectoryProvider = getServices().get(WorkerDirectoryProvider.class);
            ClassPathRegistry classPathRegistry = getServices().get(ClassPathRegistry.class);
            GroovyCompilerFactory groovyCompilerFactory = new GroovyCompilerFactory(workerDaemonFactory, inProcessWorkerFactory, forkOptionsFactory, processorDetector, jvmVersionDetector, workerDirectoryProvider, classPathRegistry);
            Compiler<GroovyJavaJointCompileSpec> delegatingCompiler = groovyCompilerFactory.newCompiler(spec);
            compiler = incremental ? delegatingCompiler : new CleaningGroovyCompiler(delegatingCompiler, getOutputs());
        }
        return compiler;
    }

    private DefaultGroovyJavaJointCompileSpec createSpec() {
        return createSpec(getSource());
    }

    private DefaultGroovyJavaJointCompileSpec createSpec(Iterable<File> source) {
        DefaultGroovyJavaJointCompileSpec spec = new DefaultGroovyJavaJointCompileSpecFactory(compileOptions).create();
        spec.setSourceFiles(source);
        spec.setDestinationDir(getDestinationDir());
        spec.setWorkingDir(getProject().getProjectDir());
        spec.setTempDir(getTemporaryDir());
        spec.setCompileClasspath(ImmutableList.copyOf(getClasspath()));
        spec.setSourceCompatibility(getSourceCompatibility());
        spec.setTargetCompatibility(getTargetCompatibility());
        spec.setAnnotationProcessorPath(Lists.newArrayList(compileOptions.getAnnotationProcessorPath() == null ? getProject().getLayout().files() : compileOptions.getAnnotationProcessorPath()));
        spec.setGroovyClasspath(Lists.newArrayList(getGroovyClasspath()));
        spec.setCompileOptions(compileOptions);
        spec.setGroovyCompileOptions(groovyCompileOptions);
        if (getOptions().isIncremental()) {
            spec.setCompilationMappingFile(new File(getTemporaryDir(), "current-compilation-mapping.txt"));
            spec.getCompilationMappingFile().delete();
        }
        if (spec.getGroovyCompileOptions().getStubDir() == null) {
            File dir = new File(getTemporaryDir(), "groovy-java-stubs");
            GFileUtils.mkdirs(dir);
            spec.getGroovyCompileOptions().setStubDir(dir);
        }
        return spec;
    }

    @Override
    @CompileClasspath
    public FileCollection getClasspath() {
        return super.getClasspath();
    }

    @Classpath
    public ConfigurableFileCollection getAstTransformClasspath() {
        return astTransformClasspath;
    }

    private void checkGroovyClasspathIsNonEmpty() {
        if (getGroovyClasspath().isEmpty()) {
            throw new InvalidUserDataException("'" + getName() + ".groovyClasspath' must not be empty. If a Groovy compile dependency is provided, "
                    + "the 'groovy-base' plugin will attempt to configure 'groovyClasspath' automatically. Alternatively, you may configure 'groovyClasspath' explicitly.");
        }
    }

    /**
     * We need to track the Java version of the JVM the Groovy compiler is running on, since the Groovy compiler produces different results depending on it.
     *
     * This should be replaced by a property on the Groovy toolchain as soon as we model these.
     *
     * @since 4.0
     */
    @Input
    protected String getGroovyCompilerJvmVersion() {
        return JavaVersion.current().getMajorVersion();
    }

    /**
     * We need to track the toolchain used by the Groovy compiler to compile Java sources.
     *
     * @since 4.0
     */
    @Nested
    protected JavaToolChain getJavaToolChain() {
        return getJavaToolChainFactory().forCompileOptions(getOptions());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Internal
    public FileTree getSource() {
        return super.getSource();
    }

    /**
     * Gets the options for the Groovy compilation. To set specific options for the nested Java compilation, use {@link
     * #getOptions()}.
     *
     * @return The Groovy compile options. Never returns null.
     */
    @Nested
    public GroovyCompileOptions getGroovyOptions() {
        return groovyCompileOptions;
    }

    /**
     * Returns the options for Java compilation.
     *
     * @return The Java compile options. Never returns null.
     */
    @Nested
    public CompileOptions getOptions() {
        return compileOptions;
    }

    /**
     * Returns the classpath containing the version of Groovy to use for compilation.
     *
     * @return The classpath.
     */
    @Classpath
    public FileCollection getGroovyClasspath() {
        return groovyClasspath;
    }

    /**
     * Sets the classpath containing the version of Groovy to use for compilation.
     *
     * @param groovyClasspath The classpath. Must not be null.
     */
    public void setGroovyClasspath(FileCollection groovyClasspath) {
        this.groovyClasspath = groovyClasspath;
    }

    @Internal
    public Compiler<GroovyJavaJointCompileSpec> getCompiler() {
        return getCompiler(createSpec(), false);
    }

    public void setCompiler(Compiler<GroovyJavaJointCompileSpec> compiler) {
        this.compiler = compiler;
    }

    @Inject
    protected JavaToolChainFactory getJavaToolChainFactory() {
        throw new UnsupportedOperationException();
    }
}
