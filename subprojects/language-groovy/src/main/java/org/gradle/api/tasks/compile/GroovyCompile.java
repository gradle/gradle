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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import org.gradle.api.Incubating;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.JavaVersion;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.internal.FeaturePreviews;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.file.FileTreeInternal;
import org.gradle.api.internal.file.TemporaryFileProvider;
import org.gradle.api.internal.tasks.JavaToolChainFactory;
import org.gradle.api.internal.tasks.compile.CleaningJavaCompiler;
import org.gradle.api.internal.tasks.compile.CompilationSourceDirs;
import org.gradle.api.internal.tasks.compile.CompilerForkUtils;
import org.gradle.api.internal.tasks.compile.DefaultGroovyJavaJointCompileSpec;
import org.gradle.api.internal.tasks.compile.DefaultGroovyJavaJointCompileSpecFactory;
import org.gradle.api.internal.tasks.compile.GroovyCompilerFactory;
import org.gradle.api.internal.tasks.compile.GroovyJavaJointCompileSpec;
import org.gradle.api.internal.tasks.compile.HasCompileOptions;
import org.gradle.api.internal.tasks.compile.incremental.IncrementalCompilerFactory;
import org.gradle.api.internal.tasks.compile.incremental.recomp.DefaultSourceFileClassNameConverter;
import org.gradle.api.internal.tasks.compile.incremental.recomp.GroovyRecompilationSpecProvider;
import org.gradle.api.internal.tasks.compile.incremental.recomp.IncrementalCompilationResult;
import org.gradle.api.internal.tasks.compile.incremental.recomp.RecompilationSpecProvider;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.model.ReplacedBy;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.CompileClasspath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.LocalState;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.WorkResult;
import org.gradle.internal.file.Deleter;
import org.gradle.internal.jvm.Jvm;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.util.GFileUtils;
import org.gradle.util.IncubationLogger;
import org.gradle.work.Incremental;
import org.gradle.work.InputChanges;

import javax.inject.Inject;
import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;

import static org.gradle.api.internal.FeaturePreviews.Feature.GROOVY_COMPILATION_AVOIDANCE;
import static org.gradle.api.internal.tasks.compile.SourceClassesMappingFileAccessor.mergeIncrementalMappingsIntoOldMappings;
import static org.gradle.api.internal.tasks.compile.SourceClassesMappingFileAccessor.readSourceClassesMappingFile;

/**
 * Compiles Groovy source files, and optionally, Java source files.
 */
@CacheableTask
public class GroovyCompile extends AbstractCompile implements HasCompileOptions {
    private FileCollection groovyClasspath;
    private final ConfigurableFileCollection astTransformationClasspath;
    private final CompileOptions compileOptions;
    private final GroovyCompileOptions groovyCompileOptions = new GroovyCompileOptions();
    private final FileCollection stableSources = getProject().files(new Callable<FileTree>() {
        @Override
        public FileTree call() {
            return getSource();
        }
    });
    private File sourceClassesMappingFile;

    public GroovyCompile() {
        ObjectFactory objectFactory = getObjectFactory();
        CompileOptions compileOptions = objectFactory.newInstance(CompileOptions.class);
        compileOptions.setIncremental(false);
        this.compileOptions = compileOptions;
        this.astTransformationClasspath = objectFactory.fileCollection();
        if (!experimentalCompilationAvoidanceEnabled()) {
            this.astTransformationClasspath.from(new Callable<FileCollection>() {
                @Override
                public FileCollection call() {
                    return getClasspath();
                }
            });
        }
        CompilerForkUtils.doNotCacheIfForkingViaExecutable(compileOptions, getOutputs());
    }

    @Override
    @CompileClasspath
    @Incremental
    public FileCollection getClasspath() {
        // Note that @CompileClasspath here is an approximation and must be fixed before de-incubating getAstTransformationClasspath()
        // See https://github.com/gradle/gradle/pull/9513
        return super.getClasspath();
    }

    /**
     * The classpath containing AST transformations and their dependencies.
     *
     * @since 5.6
     */
    @Classpath
    @Incubating
    public ConfigurableFileCollection getAstTransformationClasspath() {
        return astTransformationClasspath;
    }

    private boolean experimentalCompilationAvoidanceEnabled() {
        return getFeaturePreviews().isFeatureEnabled(GROOVY_COMPILATION_AVOIDANCE);
    }

    @TaskAction
    protected void compile(InputChanges inputChanges) {
        checkGroovyClasspathIsNonEmpty();
        warnIfCompileAvoidanceEnabled();

        GroovyJavaJointCompileSpec spec = createSpec();

        if (inputChanges != null && spec.incrementalCompilationEnabled()) {
            doIncrementalCompile(spec, inputChanges);
        } else {
            doCompile(spec, inputChanges, null);
        }
    }

    private void doIncrementalCompile(GroovyJavaJointCompileSpec spec, InputChanges inputChanges) {
        Multimap<String, String> oldMappings = readSourceClassesMappingFile(getSourceClassesMappingFile());
        getSourceClassesMappingFile().delete();

        WorkResult result = doCompile(spec, inputChanges, oldMappings);

        if (result instanceof IncrementalCompilationResult) {
            // The compilation will generate the new mapping file
            // Only merge old mappings into new mapping on incremental recompilation
            mergeIncrementalMappingsIntoOldMappings(getSourceClassesMappingFile(), getStableSources(), inputChanges, oldMappings);
        }
    }

    private WorkResult doCompile(GroovyJavaJointCompileSpec spec, InputChanges inputChanges, Multimap<String, String> sourceClassesMapping) {
        WorkResult result = getCompiler(spec, inputChanges, sourceClassesMapping).execute(spec);
        setDidWork(result.getDidWork());
        return result;
    }

    /**
     * The Groovy source-classes mapping file. Internal use only.
     *
     * @since 5.6
     */
    @LocalState
    @Incubating
    protected File getSourceClassesMappingFile() {
        if (sourceClassesMappingFile == null) {
            File tmpDir = getServices().get(TemporaryFileProvider.class).newTemporaryFile(getName());
            sourceClassesMappingFile = new File(tmpDir, "source-classes-mapping.txt");
        }
        return sourceClassesMappingFile;
    }

    private void warnIfCompileAvoidanceEnabled() {
        if (experimentalCompilationAvoidanceEnabled()) {
            IncubationLogger.incubatingFeatureUsed("Groovy compilation avoidance");
        }
    }

    private Compiler<GroovyJavaJointCompileSpec> getCompiler(GroovyJavaJointCompileSpec spec, InputChanges inputChanges, Multimap<String, String> sourceClassesMapping) {
        GroovyCompilerFactory groovyCompilerFactory = getGroovyCompilerFactory();
        Compiler<GroovyJavaJointCompileSpec> delegatingCompiler = groovyCompilerFactory.newCompiler(spec);
        CleaningJavaCompiler<GroovyJavaJointCompileSpec> cleaningGroovyCompiler = new CleaningJavaCompiler<>(delegatingCompiler, getOutputs(), getDeleter());
        if (spec.incrementalCompilationEnabled()) {
            IncrementalCompilerFactory factory = getIncrementalCompilerFactory();
            return factory.makeIncremental(
                cleaningGroovyCompiler,
                getPath(),
                getStableSources().getAsFileTree(),
                createRecompilationSpecProvider(inputChanges, sourceClassesMapping)
            );
        } else {
            return cleaningGroovyCompiler;
        }
    }

    @Inject
    protected GroovyCompilerFactory getGroovyCompilerFactory() {
        throw new UnsupportedOperationException();
    }

    private RecompilationSpecProvider createRecompilationSpecProvider(InputChanges inputChanges, Multimap<String, String> sourceClassesMapping) {
        FileCollection stableSources = getStableSources();
        return new GroovyRecompilationSpecProvider(
            getDeleter(),
            getServices().get(FileOperations.class),
            stableSources.getAsFileTree(),
            inputChanges.isIncremental(),
            () -> inputChanges.getFileChanges(stableSources).iterator(),
            new DefaultSourceFileClassNameConverter(sourceClassesMapping));
    }

    /**
     * The sources for incremental change detection.
     *
     * @since 5.6
     */
    @Incubating
    @SkipWhenEmpty
    @PathSensitive(PathSensitivity.RELATIVE) // Java source files are supported, too. Therefore we should care about the relative path.
    @InputFiles
    protected FileCollection getStableSources() {
        return stableSources;
    }

    /**
     * Injects and returns an instance of {@link IncrementalCompilerFactory}.
     *
     * @since 5.6
     */
    @Inject
    protected IncrementalCompilerFactory getIncrementalCompilerFactory() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected Deleter getDeleter() {
        throw new UnsupportedOperationException("Decorator takes care of injection");
    }

    @Inject
    protected ProjectLayout getProjectLayout() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected ObjectFactory getObjectFactory() {
        throw new UnsupportedOperationException();
    }

    private FileCollection determineGroovyCompileClasspath() {
        if (experimentalCompilationAvoidanceEnabled()) {
            return astTransformationClasspath.plus(getClasspath());
        } else {
            return getClasspath();
        }
    }

    private static void validateIncrementalCompilationOptions(List<File> sourceRoots, boolean annotationProcessingConfigured) {
        if (sourceRoots.isEmpty()) {
            throw new InvalidUserDataException("Unable to infer source roots. Incremental Groovy compilation requires the source roots. Change the configuration of your sources or disable incremental Groovy compilation.");
        }

        if (annotationProcessingConfigured) {
            throw new InvalidUserDataException("Enabling incremental compilation and configuring Java annotation processors for Groovy compilation is not allowed. Disable incremental Groovy compilation or remove the Java annotation processor configuration.");
        }
    }

    private GroovyJavaJointCompileSpec createSpec() {
        DefaultGroovyJavaJointCompileSpec spec = new DefaultGroovyJavaJointCompileSpecFactory(compileOptions).create();

        FileTreeInternal stableSourcesAsFileTree = (FileTreeInternal) getStableSources().getAsFileTree();
        List<File> sourceRoots = CompilationSourceDirs.inferSourceRoots(stableSourcesAsFileTree);

        spec.setSourcesRoots(sourceRoots);
        spec.setSourceFiles(stableSourcesAsFileTree);
        spec.setDestinationDir(getDestinationDirectory().getAsFile().get());
        spec.setWorkingDir(getProjectLayout().getProjectDirectory().getAsFile());
        spec.setTempDir(getTemporaryDir());
        spec.setCompileClasspath(ImmutableList.copyOf(determineGroovyCompileClasspath()));
        spec.setSourceCompatibility(getSourceCompatibility());
        spec.setTargetCompatibility(getTargetCompatibility());
        spec.setAnnotationProcessorPath(Lists.newArrayList(compileOptions.getAnnotationProcessorPath() == null ? getProjectLayout().files() : compileOptions.getAnnotationProcessorPath()));
        spec.setGroovyClasspath(Lists.newArrayList(getGroovyClasspath()));
        spec.setCompileOptions(compileOptions);
        spec.setGroovyCompileOptions(groovyCompileOptions);
        if (getOptions().isIncremental()) {
            validateIncrementalCompilationOptions(sourceRoots, spec.annotationProcessingConfigured());
            spec.setCompilationMappingFile(getSourceClassesMappingFile());
        }
        if (spec.getGroovyCompileOptions().getStubDir() == null) {
            File dir = new File(getTemporaryDir(), "groovy-java-stubs");
            GFileUtils.mkdirs(dir);
            spec.getGroovyCompileOptions().setStubDir(dir);
        }
        spec.getCompileOptions().getForkOptions().setExecutable(Jvm.current().getJavaExecutable().getAbsolutePath());

        return spec;
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
    @SuppressWarnings("deprecation")
    @Deprecated
    protected org.gradle.jvm.toolchain.JavaToolChain getJavaToolChain() {
        return getJavaToolChainFactory().forCompileOptions(getOptions());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @ReplacedBy("stableSources")
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

    @Inject
    protected JavaToolChainFactory getJavaToolChainFactory() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected FeaturePreviews getFeaturePreviews() {
        throw new UnsupportedOperationException();
    }
}
