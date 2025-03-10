/*
 * Copyright 2016 the original author or authors.
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

import com.sun.source.util.JavacTask;
import com.sun.tools.javac.api.JavacTool;
import com.sun.tools.javac.util.Context;
import org.gradle.api.internal.tasks.compile.incremental.compilerapi.constants.ConstantsAnalysisResult;
import org.gradle.internal.Cast;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.classloader.ClassLoaderFactory;
import org.gradle.internal.classloader.DefaultClassLoaderFactory;
import org.gradle.internal.classloader.FilteringClassLoader;
import org.gradle.internal.classloader.VisitableURLClassLoader;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.internal.reflect.DirectInstantiator;

import javax.lang.model.SourceVersion;
import javax.tools.DiagnosticListener;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Subset replacement for {@link javax.tools.ToolProvider} that avoids the application class loader.
 */
public class JdkTools {

    private final ClassLoader isolatedToolsLoader;

    private Class<JavaCompiler.CompilationTask> incrementalCompileTaskClass;

    JdkTools(List<File> compilerPlugins) {
        DefaultClassLoaderFactory defaultClassLoaderFactory = new DefaultClassLoaderFactory();
        ClassLoader filteringClassLoader = getSystemFilteringClassLoader(defaultClassLoaderFactory);
        isolatedToolsLoader = VisitableURLClassLoader.fromClassPath("jdk-tools", filteringClassLoader, DefaultClassPath.of(compilerPlugins));
    }

    private ClassLoader getSystemFilteringClassLoader(ClassLoaderFactory classLoaderFactory) {
        FilteringClassLoader.Spec filterSpec = new FilteringClassLoader.Spec();
        filterSpec.allowPackage("com.sun.tools");
        filterSpec.allowPackage("com.sun.source");
        return classLoaderFactory.createFilteringClassLoader(
            this.getClass().getClassLoader(),
            filterSpec
        );
    }

    public ContextAwareJavaCompiler getSystemJavaCompiler() {
        return new DefaultIncrementalAwareCompiler(buildJavaCompiler());
    }

    private JavacTool buildJavaCompiler() {
        return JavacTool.create();
    }

    private class DefaultIncrementalAwareCompiler implements IncrementalCompilationAwareJavaCompiler {
        private final JavacTool delegate;

        private DefaultIncrementalAwareCompiler(JavacTool delegate) {
            this.delegate = delegate;
        }

        @Override
        public CompilationTask getTask(Writer out, JavaFileManager fileManager, DiagnosticListener<? super JavaFileObject> diagnosticListener, Iterable<String> options, Iterable<String> classes, Iterable<? extends JavaFileObject> compilationUnits) {
            return delegate.getTask(out, fileManager, diagnosticListener, options, classes, compilationUnits);
        }

        @Override
        public JavacTask getTask(Writer out, JavaFileManager fileManager, DiagnosticListener<? super JavaFileObject> diagnosticListener, Iterable<String> options, Iterable<String> classes, Iterable<? extends JavaFileObject> compilationUnits, Context context) {
            return delegate.getTask(out, fileManager, diagnosticListener, options, classes, compilationUnits, context);
        }

        @Override
        public StandardJavaFileManager getStandardFileManager(DiagnosticListener<? super JavaFileObject> diagnosticListener, Locale locale, Charset charset) {
            return delegate.getStandardFileManager(diagnosticListener, locale, charset);
        }

        @Override
        public String name() {
            return delegate.name();
        }

        @Override
        public int run(InputStream in, OutputStream out, OutputStream err, String... arguments) {
            return delegate.run(in, out, err, arguments);
        }

        @Override
        public Set<SourceVersion> getSourceVersions() {
            return delegate.getSourceVersions();
        }

        @Override
        public int isSupportedOption(String option) {
            return delegate.isSupportedOption(option);
        }

        @Override
        public JavaCompiler.CompilationTask makeIncremental(JavaCompiler.CompilationTask task, Map<String, Set<String>> sourceToClassMapping,
                                                            ConstantsAnalysisResult constantsAnalysisResult, CompilationSourceDirs compilationSourceDirs,
                                                            CompilationClassBackupService classBackupService
        ) {
            ensureCompilerTask();
            // task (JavacTaskImpl) classloader: app classloader
            // incrementalCompileTaskClass classloader: jdk-tools
            return DirectInstantiator.instantiate(incrementalCompileTaskClass, task,
                (Function<File, Optional<String>>) compilationSourceDirs::relativize,
                (Consumer<String>) classBackupService::maybeBackupClassFile,
                (Consumer<Map<String, Set<String>>>) sourceToClassMapping::putAll,
                (BiConsumer<String, String>) constantsAnalysisResult::addPublicDependent,
                (BiConsumer<String, String>) constantsAnalysisResult::addPrivateDependent
            );
        }
    }

    private void ensureCompilerTask() {
        if (incrementalCompileTaskClass == null) {
            synchronized (this) {
                try {
                    incrementalCompileTaskClass = Cast.uncheckedCast(isolatedToolsLoader.loadClass("org.gradle.internal.compiler.java.IncrementalCompileTask"));
                } catch (ClassNotFoundException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            }
        }
    }
}
