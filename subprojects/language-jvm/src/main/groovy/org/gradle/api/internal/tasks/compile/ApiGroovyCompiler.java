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

import com.google.common.collect.Iterables;
import groovy.lang.Binding;
import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyShell;
import groovy.lang.GroovySystem;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
import org.codehaus.groovy.control.messages.SimpleMessage;
import org.codehaus.groovy.tools.javac.JavaAwareCompilationUnit;
import org.codehaus.groovy.tools.javac.JavaCompiler;
import org.gradle.api.GradleException;
import org.gradle.api.internal.tasks.SimpleWorkResult;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.WorkResult;
import org.gradle.internal.classloader.FilteringClassLoader;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.util.VersionNumber;

import java.io.File;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ApiGroovyCompiler implements org.gradle.language.base.internal.compile.Compiler<GroovyJavaJointCompileSpec>, Serializable {
    private final Compiler<JavaCompileSpec> javaCompiler;

    public ApiGroovyCompiler(Compiler<JavaCompileSpec> javaCompiler) {
        this.javaCompiler = javaCompiler;
    }

    public WorkResult execute(final GroovyJavaJointCompileSpec spec) {
        CompilerConfiguration configuration = new CompilerConfiguration();
        configuration.setVerbose(spec.getGroovyCompileOptions().isVerbose());
        configuration.setSourceEncoding(spec.getGroovyCompileOptions().getEncoding());
        configuration.setTargetBytecode(spec.getTargetCompatibility());
        configuration.setTargetDirectory(spec.getDestinationDir());
        canonicalizeValues(spec.getGroovyCompileOptions().getOptimizationOptions());
        if (spec.getGroovyCompileOptions().getConfigurationScript() != null) {
            applyConfigurationScript(spec.getGroovyCompileOptions().getConfigurationScript(), configuration);
        }
        try {
            configuration.setOptimizationOptions(spec.getGroovyCompileOptions().getOptimizationOptions());
        } catch (NoSuchMethodError ignored) { /* method was only introduced in Groovy 1.8 */ }
        Map<String, Object> jointCompilationOptions = new HashMap<String, Object>();
        jointCompilationOptions.put("stubDir", spec.getGroovyCompileOptions().getStubDir());
        jointCompilationOptions.put("keepStubs", spec.getGroovyCompileOptions().isKeepStubs());
        configuration.setJointCompilationOptions(jointCompilationOptions);

        URLClassLoader classPathLoader = new GroovyCompileTransformingClassLoader(new DefaultClassPath(spec.getClasspath()));
        GroovyClassLoader compileClasspathClassLoader = new GroovyClassLoader(classPathLoader, null);

        FilteringClassLoader groovyCompilerClassLoader = new FilteringClassLoader(GroovyClassLoader.class.getClassLoader());
        groovyCompilerClassLoader.allowPackage("org.codehaus.groovy");
        groovyCompilerClassLoader.allowPackage("groovy");
        // Disallow classes from Groovy Jar that reference external classes. Such classes must be loaded from astTransformClassLoader,
        // or a NoClassDefFoundError will occur. Essentially this is drawing a line between the Groovy compiler and the Groovy
        // library, albeit only for selected classes that run a high risk of being statically referenced from a transform.
        groovyCompilerClassLoader.disallowClass("groovy.util.GroovyTestCase");
        groovyCompilerClassLoader.disallowClass("groovy.servlet.GroovyServlet");

        // AST transforms need their own class loader that shares compiler classes with the compiler itself
        final GroovyClassLoader astTransformClassLoader = new GroovyClassLoader(groovyCompilerClassLoader, null);
        // can't delegate to compileClasspathLoader because this would result in ASTTransformation interface
        // (which is implemented by the transform class) being loaded by compileClasspathClassLoader (which is
        // where the transform class is loaded from)
        for (File file : spec.getClasspath()) {
            astTransformClassLoader.addClasspath(file.getPath());
        }

        JavaAwareCompilationUnit unit = new JavaAwareCompilationUnit(configuration, compileClasspathClassLoader) {
            @Override
            public GroovyClassLoader getTransformLoader() {
                return astTransformClassLoader;
            }
        };
        unit.addSources(Iterables.toArray(spec.getSource(), File.class));
        unit.setCompilerFactory(new org.codehaus.groovy.tools.javac.JavaCompilerFactory() {
            public JavaCompiler createCompiler(final CompilerConfiguration config) {
                return new JavaCompiler() {
                    public void compile(List<String> files, CompilationUnit cu) {
                        spec.setSource(spec.getSource().filter(new Spec<File>() {
                            public boolean isSatisfiedBy(File file) {
                                return file.getName().endsWith(".java");
                            }
                        }));
                        spec.getCompileOptions().getCompilerArgs().add("-sourcepath");
                        spec.getCompileOptions().getCompilerArgs().add(((File) config.getJointCompilationOptions().get("stubDir")).getAbsolutePath());
                        try {
                            javaCompiler.execute(spec);
                        } catch (CompilationFailedException e) {
                            cu.getErrorCollector().addFatalError(new SimpleMessage(e.getMessage(), cu));
                        }
                    }
                };
            }
        });

        try {
            unit.compile();
        } catch (org.codehaus.groovy.control.CompilationFailedException e) {
            System.err.println(e.getMessage());
            throw new CompilationFailedException();
        }

        return new SimpleWorkResult(true);
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

}
