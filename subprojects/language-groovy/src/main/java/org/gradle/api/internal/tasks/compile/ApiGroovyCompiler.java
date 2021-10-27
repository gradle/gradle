/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.tasks.compile;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import groovy.lang.Binding;
import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyShell;
import groovy.lang.GroovySystem;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.InnerClassNode;
import org.codehaus.groovy.classgen.GeneratorContext;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.control.customizers.CompilationCustomizer;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
import org.codehaus.groovy.control.messages.SimpleMessage;
import org.codehaus.groovy.tools.javac.JavaAwareCompilationUnit;
import org.codehaus.groovy.tools.javac.JavaCompiler;
import org.codehaus.groovy.tools.javac.JavaCompilerFactory;
import org.gradle.api.GradleException;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.internal.classloading.GroovySystemLoader;
import org.gradle.api.internal.classloading.GroovySystemLoaderFactory;
import org.gradle.api.tasks.WorkResult;
import org.gradle.internal.classloader.ClassLoaderUtils;
import org.gradle.internal.classloader.DefaultClassLoaderFactory;
import org.gradle.internal.classloader.FilteringClassLoader;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.util.internal.VersionNumber;

import java.io.File;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.gradle.internal.FileUtils.hasExtension;

public class ApiGroovyCompiler implements org.gradle.language.base.internal.compile.Compiler<GroovyJavaJointCompileSpec>, Serializable {
    private final Compiler<JavaCompileSpec> javaCompiler;
    private final ProjectLayout projectLayout;

    public ApiGroovyCompiler(Compiler<JavaCompileSpec> javaCompiler, ProjectLayout projectLayout) {
        this.javaCompiler = javaCompiler;
        this.projectLayout = projectLayout;
    }

    private static abstract class IncrementalCompilationCustomizer extends CompilationCustomizer {
        static IncrementalCompilationCustomizer fromSpec(GroovyJavaJointCompileSpec spec, ApiCompilerResult result) {
            if (spec.incrementalCompilationEnabled()) {
                return new TrackingClassGenerationCompilationCustomizer(new CompilationSourceDirs(spec), result);
            } else {
                return new NoOpCompilationCustomizer();
            }
        }

        public IncrementalCompilationCustomizer() {
            super(CompilePhase.CLASS_GENERATION);
        }

        abstract void addToConfiguration(CompilerConfiguration configuration);
    }

    private static class NoOpCompilationCustomizer extends IncrementalCompilationCustomizer {

        @Override
        public void addToConfiguration(CompilerConfiguration configuration) {
        }

        @Override
        public void call(SourceUnit source, GeneratorContext context, ClassNode classNode) throws org.codehaus.groovy.control.CompilationFailedException {
            throw new UnsupportedOperationException();
        }
    }

    private static class TrackingClassGenerationCompilationCustomizer extends IncrementalCompilationCustomizer {
        private final CompilationSourceDirs compilationSourceDirs;
        private final ApiCompilerResult result;

        private TrackingClassGenerationCompilationCustomizer(CompilationSourceDirs compilationSourceDirs, ApiCompilerResult result) {
            this.compilationSourceDirs = compilationSourceDirs;
            this.result = result;
        }

        @Override
        public void call(SourceUnit source, GeneratorContext context, ClassNode classNode) {
            inspectClassNode(source, classNode);
        }

        private void inspectClassNode(SourceUnit sourceUnit, ClassNode classNode) {
            String relativePath = compilationSourceDirs.relativize(new File(sourceUnit.getSource().getURI().getPath())).orElseThrow(IllegalStateException::new);
            result.getSourceClassesMapping().computeIfAbsent(relativePath, key -> new HashSet<>()).add(classNode.getName());
            Iterator<InnerClassNode> iterator = classNode.getInnerClasses();
            while (iterator.hasNext()) {
                inspectClassNode(sourceUnit, iterator.next());
            }
        }

        @Override
        public void addToConfiguration(CompilerConfiguration configuration) {
            configuration.addCompilationCustomizers(this);
        }
    }

    private File[] getSortedSourceFiles(GroovyJavaJointCompileSpec spec) {
        // Sort source files to work around https://issues.apache.org/jira/browse/GROOVY-7966
        File[] sortedSourceFiles = Iterables.toArray(spec.getSourceFiles(), File.class);
        Arrays.sort(sortedSourceFiles);
        return sortedSourceFiles;
    }

    @Override
    public WorkResult execute(final GroovyJavaJointCompileSpec spec) {
        ApiCompilerResult result = new ApiCompilerResult();
        result.getAnnotationProcessingResult().setFullRebuildCause("Incremental annotation processing is not supported by Groovy.");
        GroovySystemLoaderFactory groovySystemLoaderFactory = new GroovySystemLoaderFactory();
        ClassLoader compilerClassLoader = this.getClass().getClassLoader();
        GroovySystemLoader compilerGroovyLoader = groovySystemLoaderFactory.forClassLoader(compilerClassLoader);

        CompilerConfiguration configuration = new CompilerConfiguration();
        configuration.setVerbose(spec.getGroovyCompileOptions().isVerbose());
        configuration.setSourceEncoding(spec.getGroovyCompileOptions().getEncoding());
        configuration.setTargetBytecode(spec.getTargetCompatibility());
        configuration.setTargetDirectory(spec.getDestinationDir());
        canonicalizeValues(spec.getGroovyCompileOptions().getOptimizationOptions());

        VersionNumber version = parseGroovyVersion();
        if (version.compareTo(VersionNumber.parse("2.5")) >= 0) {
            configuration.setParameters(spec.getGroovyCompileOptions().isParameters());
        } else if (spec.getGroovyCompileOptions().isParameters()) {
            throw new GradleException("Using Groovy compiler flag '--parameters' requires Groovy 2.5+ but found Groovy " + version);
        }

        IncrementalCompilationCustomizer customizer = IncrementalCompilationCustomizer.fromSpec(spec, result);
        customizer.addToConfiguration(configuration);

        if (spec.getGroovyCompileOptions().getConfigurationScript() != null) {
            applyConfigurationScript(spec.getGroovyCompileOptions().getConfigurationScript(), configuration);
        }
        try {
            configuration.setOptimizationOptions(spec.getGroovyCompileOptions().getOptimizationOptions());
        } catch (NoSuchMethodError ignored) { /* method was only introduced in Groovy 1.8 */ }
        Map<String, Object> jointCompilationOptions = new HashMap<String, Object>();
        final File stubDir = spec.getGroovyCompileOptions().getStubDir();
        stubDir.mkdirs();
        jointCompilationOptions.put("stubDir", stubDir);
        jointCompilationOptions.put("keepStubs", spec.getGroovyCompileOptions().isKeepStubs());
        configuration.setJointCompilationOptions(jointCompilationOptions);

        ClassLoader classPathLoader;
        if (version.compareTo(VersionNumber.parse("2.0")) < 0) {
            // using a transforming classloader is only required for older buggy Groovy versions
            classPathLoader = new GroovyCompileTransformingClassLoader(getExtClassLoader(), DefaultClassPath.of(spec.getCompileClasspath()));
        } else {
            classPathLoader = new DefaultClassLoaderFactory().createIsolatedClassLoader("api-groovy-compile-loader", DefaultClassPath.of(spec.getCompileClasspath()));
        }
        GroovyClassLoader compileClasspathClassLoader = new GroovyClassLoader(classPathLoader, null);
        GroovySystemLoader compileClasspathLoader = groovySystemLoaderFactory.forClassLoader(classPathLoader);

        FilteringClassLoader.Spec groovyCompilerClassLoaderSpec = new FilteringClassLoader.Spec();
        groovyCompilerClassLoaderSpec.allowPackage("org.codehaus.groovy");
        groovyCompilerClassLoaderSpec.allowPackage("groovy");
        groovyCompilerClassLoaderSpec.allowPackage("groovyjarjarasm");
        // Disallow classes from Groovy Jar that reference external classes. Such classes must be loaded from astTransformClassLoader,
        // or a NoClassDefFoundError will occur. Essentially this is drawing a line between the Groovy compiler and the Groovy
        // library, albeit only for selected classes that run a high risk of being statically referenced from a transform.
        groovyCompilerClassLoaderSpec.disallowClass("groovy.util.GroovyTestCase");
        groovyCompilerClassLoaderSpec.disallowClass("org.codehaus.groovy.transform.NotYetImplementedASTTransformation");
        groovyCompilerClassLoaderSpec.disallowPackage("groovy.servlet");
        FilteringClassLoader groovyCompilerClassLoader = new FilteringClassLoader(GroovyClassLoader.class.getClassLoader(), groovyCompilerClassLoaderSpec);

        // AST transforms need their own class loader that shares compiler classes with the compiler itself
        final GroovyClassLoader astTransformClassLoader = new GroovyClassLoader(groovyCompilerClassLoader, null);
        // can't delegate to compileClasspathLoader because this would result in ASTTransformation interface
        // (which is implemented by the transform class) being loaded by compileClasspathClassLoader (which is
        // where the transform class is loaded from)
        for (File file : spec.getCompileClasspath()) {
            astTransformClassLoader.addClasspath(file.getPath());
        }
        JavaAwareCompilationUnit unit = new JavaAwareCompilationUnit(configuration, compileClasspathClassLoader) {
            @Override
            public GroovyClassLoader getTransformLoader() {
                return astTransformClassLoader;
            }
        };

        final boolean shouldProcessAnnotations = shouldProcessAnnotations(spec);
        if (shouldProcessAnnotations) {
            // If an annotation processor is detected, we need to force Java stub generation, so the we can process annotations on Groovy classes
            // We are forcing stub generation by tricking the groovy compiler into thinking there are java files to compile.
            // All java files are just passed to the compile method of the JavaCompiler and aren't processed internally by the Groovy Compiler.
            // Since we're maintaining our own list of Java files independent of what's passed by the Groovy compiler, adding a non-existent java file
            // to the sources won't cause any issues.
            unit.addSources(new File[]{new File("ForceStubGeneration.java")});
        }
        unit.addSources(getSortedSourceFiles(spec));

        unit.setCompilerFactory(new JavaCompilerFactory() {
            @Override
            public JavaCompiler createCompiler(final CompilerConfiguration config) {
                return new JavaCompiler() {
                    @Override
                    public void compile(List<String> files, CompilationUnit cu) {
                        if (shouldProcessAnnotations) {
                            // In order for the Groovy stubs to have annotation processors invoked against them, they must be compiled as source.
                            // Classes compiled as a result of being on the -sourcepath do not have the annotation processor run against them
                            spec.setSourceFiles(Iterables.concat(spec.getSourceFiles(), projectLayout.files(stubDir).getAsFileTree()));
                        } else {
                            // When annotation processing isn't required, it's better to add the Groovy stubs as part of the source path.
                            // This allows compilations to complete faster, because only the Groovy stubs that are needed by the java source are compiled.
                            ImmutableList.Builder<File> sourcepathBuilder = ImmutableList.builder();
                            sourcepathBuilder.add(stubDir);
                            if (spec.getCompileOptions().getSourcepath() != null) {
                                sourcepathBuilder.addAll(spec.getCompileOptions().getSourcepath());
                            }
                            spec.getCompileOptions().setSourcepath(sourcepathBuilder.build());
                        }

                        spec.setSourceFiles(Iterables.filter(spec.getSourceFiles(), new Predicate<File>() {
                            @Override
                            public boolean apply(File file) {
                                return hasExtension(file, ".java");
                            }
                        }));

                        try {
                            WorkResult javaCompilerResult = javaCompiler.execute(spec);
                            if (javaCompilerResult instanceof ApiCompilerResult) {
                                result.getSourceClassesMapping().putAll(((ApiCompilerResult) javaCompilerResult).getSourceClassesMapping());
                            }
                        } catch (CompilationFailedException e) {
                            cu.getErrorCollector().addFatalError(new SimpleMessage(e.getMessage(), cu));
                        }
                    }
                };
            }
        });

        try {
            unit.compile();
            return result;
        } catch (org.codehaus.groovy.control.CompilationFailedException e) {
            System.err.println(e.getMessage());
            // Explicit flush, System.err is an auto-flushing PrintWriter unless it is replaced.
            System.err.flush();
            throw new CompilationFailedException();
        } finally {
            // Remove compile and AST types from the Groovy loader
            compilerGroovyLoader.discardTypesFrom(classPathLoader);
            compilerGroovyLoader.discardTypesFrom(astTransformClassLoader);
            //Discard the compile loader
            compileClasspathLoader.shutdown();
            CompositeStoppable.stoppable(classPathLoader, astTransformClassLoader).stop();
        }
    }

    private static boolean shouldProcessAnnotations(GroovyJavaJointCompileSpec spec) {
        return spec.getGroovyCompileOptions().isJavaAnnotationProcessing() && spec.annotationProcessingConfigured();
    }

    private void applyConfigurationScript(File configScript, CompilerConfiguration configuration) {
        VersionNumber version = parseGroovyVersion();
        if (version.compareTo(VersionNumber.parse("2.1")) < 0) {
            throw new GradleException("Using a Groovy compiler configuration script requires Groovy 2.1+ but found Groovy " + version + "");
        }
        Binding binding = new Binding();
        binding.setVariable("configuration", configuration);

        CompilerConfiguration configuratorConfig = new CompilerConfiguration();
        ImportCustomizer customizer = new ImportCustomizer();
        customizer.addStaticStars("org.codehaus.groovy.control.customizers.builder.CompilerCustomizationBuilder");
        configuratorConfig.addCompilationCustomizers(customizer);

        GroovyShell shell = new GroovyShell(binding, configuratorConfig);
        try {
            shell.evaluate(configScript);
        } catch (Exception e) {
            throw new GradleException("Could not execute Groovy compiler configuration script: " + configScript.getAbsolutePath(), e);
        }
    }

    private VersionNumber parseGroovyVersion() {
        String version;
        try {
            version = GroovySystem.getVersion();
        } catch (NoSuchMethodError e) {
            // for Groovy <1.6, we need to call org.codehaus.groovy.runtime.InvokerHelper#getVersion
            try {
                Class<?> ih = Class.forName("org.codehaus.groovy.runtime.InvokerHelper");
                Method getVersion = ih.getDeclaredMethod("getVersion");
                version = (String) getVersion.invoke(ih);
            } catch (Exception e1) {
                throw new GradleException("Unable to determine Groovy version.", e1);
            }
        }
        return VersionNumber.parse(version);
    }

    // Make sure that map only contains Boolean.TRUE and Boolean.FALSE values and no other Boolean instances.
    // This is necessary because:
    // 1. serialization/deserialization of the compile spec doesn't preserve Boolean.TRUE/Boolean.FALSE but creates new instances
    // 1. org.codehaus.groovy.classgen.asm.WriterController makes identity comparisons
    private void canonicalizeValues(Map<String, Boolean> options) {
        for (String key : options.keySet()) {
            // unboxing and boxing does the trick
            boolean value = options.get(key);
            options.put(key, value);
        }
    }

    private ClassLoader getExtClassLoader() {
        return ClassLoaderUtils.getPlatformClassLoader();
    }
}
