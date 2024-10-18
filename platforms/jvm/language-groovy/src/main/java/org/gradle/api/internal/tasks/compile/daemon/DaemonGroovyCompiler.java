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

package org.gradle.api.internal.tasks.compile.daemon;

import com.google.common.collect.Iterables;
import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.api.internal.tasks.compile.ApiCompilerResult;
import org.gradle.api.internal.tasks.compile.MinimalCompilerDaemonForkOptionsConverter;
import org.gradle.api.internal.tasks.compile.GroovyJavaJointCompileSpec;
import org.gradle.api.internal.tasks.compile.MinimalGroovyCompilerDaemonForkOptions;
import org.gradle.api.internal.tasks.compile.MinimalJavaCompilerDaemonForkOptions;
import org.gradle.api.internal.tasks.compile.incremental.compilerapi.constants.ConstantsAnalysisResult;
import org.gradle.api.internal.tasks.compile.incremental.processing.AnnotationProcessingResult;
import org.gradle.api.problems.Severity;
import org.gradle.api.problems.internal.GradleCoreProblemGroup;
import org.gradle.api.problems.internal.InternalProblemReporter;
import org.gradle.initialization.ClassLoaderRegistry;
import org.gradle.internal.classloader.FilteringClassLoader;
import org.gradle.internal.classloader.VisitableURLClassLoader;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.internal.jvm.JavaInfo;
import org.gradle.internal.jvm.JpmsConfiguration;
import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.jvm.inspection.JvmVersionDetector;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.process.JavaForkOptions;
import org.gradle.process.internal.JavaForkOptionsFactory;
import org.gradle.workers.internal.DaemonForkOptions;
import org.gradle.workers.internal.DaemonForkOptionsBuilder;
import org.gradle.workers.internal.HierarchicalClassLoaderStructure;
import org.gradle.workers.internal.KeepAliveMode;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

public class DaemonGroovyCompiler extends AbstractDaemonCompiler<GroovyJavaJointCompileSpec> {
    private final Class<? extends Compiler<GroovyJavaJointCompileSpec>> compilerClass;
    private final static Iterable<String> SHARED_PACKAGES = Arrays.asList("groovy", "org.codehaus.groovy", "groovyjarjarantlr", "groovyjarjarasm", "groovyjarjarcommonscli", "org.apache.tools.ant", "com.sun.tools.javac");
    private final ClassPathRegistry classPathRegistry;
    private final ClassLoaderRegistry classLoaderRegistry;
    private final JavaForkOptionsFactory forkOptionsFactory;
    private final File daemonWorkingDir;
    private final JvmVersionDetector jvmVersionDetector;
    private final InternalProblemReporter problemReporter;

    public DaemonGroovyCompiler(
        File daemonWorkingDir,
        Class<? extends Compiler<GroovyJavaJointCompileSpec>> compilerClass,
        ClassPathRegistry classPathRegistry,
        CompilerWorkerExecutor compilerWorkerExecutor,
        ClassLoaderRegistry classLoaderRegistry,
        JavaForkOptionsFactory forkOptionsFactory,
        JvmVersionDetector jvmVersionDetector,
        InternalProblemReporter problemReporter
    ) {
        super(compilerWorkerExecutor);
        this.compilerClass = compilerClass;
        this.classPathRegistry = classPathRegistry;
        this.classLoaderRegistry = classLoaderRegistry;
        this.forkOptionsFactory = forkOptionsFactory;
        this.daemonWorkingDir = daemonWorkingDir;
        this.jvmVersionDetector = jvmVersionDetector;
        this.problemReporter = problemReporter;
    }

    @Override
    protected CompilerWorkerExecutor.CompilerParameters getCompilerParameters(GroovyJavaJointCompileSpec spec) {
        return new GroovyCompilerParameters(compilerClass.getName(), new Object[]{classPathRegistry.getClassPath("JAVA-COMPILER-PLUGIN").getAsFiles()}, spec);
    }

    @Override
    protected DaemonForkOptions toDaemonForkOptions(GroovyJavaJointCompileSpec spec) {
        MinimalJavaCompilerDaemonForkOptions javaOptions = spec.getCompileOptions().getForkOptions();
        MinimalGroovyCompilerDaemonForkOptions groovyOptions = spec.getGroovyCompileOptions().getForkOptions();
        // Ant is optional dependency of groovy(-all) module but mandatory dependency of Groovy compiler;
        // that's why we add it here. The following assumes that any Groovy compiler version supported by Gradle
        // is compatible with Gradle's current Ant version.
        Collection<File> antFiles = classPathRegistry.getClassPath("ANT").getAsFiles();
        Iterable<File> classpath = Iterables.concat(spec.getGroovyClasspath(), antFiles);
        VisitableURLClassLoader.Spec targetGroovyClasspath = new VisitableURLClassLoader.Spec("worker-loader", DefaultClassPath.of(classpath).getAsURLs());

        ClassPath languageGroovyClasspath = classPathRegistry.getClassPath("GROOVY-COMPILER");

        FilteringClassLoader.Spec gradleAndUserFilter = getMinimalGradleFilter();

        for (String sharedPackage : SHARED_PACKAGES) {
            gradleAndUserFilter.allowPackage(sharedPackage);
        }

        JavaForkOptions javaForkOptions = new MinimalCompilerDaemonForkOptionsConverter(forkOptionsFactory).transform(mergeForkOptions(javaOptions, groovyOptions));
        javaForkOptions.setWorkingDir(daemonWorkingDir);
        javaForkOptions.setExecutable(javaOptions.getExecutable());
        if (jvmVersionDetector.getJavaVersionMajor(javaForkOptions.getExecutable()) >= 9) {
            javaForkOptions.jvmArgs(JpmsConfiguration.GROOVY_JPMS_ARGS);
        } else {
            // In JDK 8 and below, we need to attach the 'tools.jar' to the classpath.
            File javaExecutable = new File(javaForkOptions.getExecutable());
            JavaInfo jvm = Jvm.forHome(javaExecutable.getParentFile().getParentFile());
            File toolsJar = jvm.getToolsJar();
            if (toolsJar == null) {
                String contextualMessage = String.format("The 'tools.jar' cannot be found in the JDK '%s'.", jvm.getJavaHome());
                throw problemReporter.throwing(problemSpec -> problemSpec
                    .id("groovy-daemon-compiler", "Missing tools.jar", GradleCoreProblemGroup.compilation().groovy())
                    .contextualLabel(contextualMessage)
                    .solution("Check if the installation is not a JRE but a JDK.")
                    .severity(Severity.ERROR)
                    .withException(new IllegalStateException(contextualMessage))
                );
            } else {
                languageGroovyClasspath = languageGroovyClasspath.plus(Collections.singletonList(toolsJar));
            }
        }

        VisitableURLClassLoader.Spec compilerClasspath = new VisitableURLClassLoader.Spec("compiler-loader", languageGroovyClasspath.getAsURLs());
        HierarchicalClassLoaderStructure classLoaderStructure =
            new HierarchicalClassLoaderStructure(classLoaderRegistry.getGradleWorkerExtensionSpec())
                .withChild(getMinimalGradleFilter())
                .withChild(targetGroovyClasspath)
                .withChild(gradleAndUserFilter)
                .withChild(compilerClasspath);

        return new DaemonForkOptionsBuilder(forkOptionsFactory)
            .javaForkOptions(javaForkOptions)
            .keepAliveMode(KeepAliveMode.SESSION)
            .withClassLoaderStructure(classLoaderStructure)
            .build();
    }

    private static FilteringClassLoader.Spec getMinimalGradleFilter() {
        // Allow only certain things from the underlying classloader
        FilteringClassLoader.Spec gradleFilterSpec = new FilteringClassLoader.Spec();

        // Logging
        gradleFilterSpec.allowPackage("org.slf4j");

        // Native Services
        gradleFilterSpec.allowPackage("net.rubygrapefruit.platform");

        // Inject
        gradleFilterSpec.allowPackage("javax.inject");

        // Gradle stuff
        gradleFilterSpec.allowPackage("org.gradle");

        // Guava
        gradleFilterSpec.allowPackage("com.google");

        // This should come from the compiler classpath only
        gradleFilterSpec.disallowPackage("org.gradle.api.internal.tasks.compile");

        /*
         * This shouldn't be necessary, but currently is because the worker API handles return types differently
         * depending on whether you use process isolation or classpath isolation. In the former case, the return
         * value is serialized and deserialized, so the correct class is returned. In the latter case, the result
         * is returned directly, which means it is not an instance of the expected class unless we allow that class
         * to leak through here. Should be fixed in the worker API, so that it always serializes/deserializes results.
         */
        gradleFilterSpec.allowClass(ApiCompilerResult.class);
        gradleFilterSpec.allowClass(AnnotationProcessingResult.class);
        gradleFilterSpec.allowClass(ConstantsAnalysisResult.class);

        return gradleFilterSpec;
    }

    public static class GroovyCompilerParameters extends CompilerWorkerExecutor.CompilerParameters {
        private final GroovyJavaJointCompileSpec compileSpec;

        public GroovyCompilerParameters(String compilerClassName, Object[] compilerInstanceParameters, GroovyJavaJointCompileSpec compileSpec) {
            super(compilerClassName, compilerInstanceParameters);
            this.compileSpec = compileSpec;
        }

        @Override
        public GroovyJavaJointCompileSpec getCompileSpec() {
            return compileSpec;
        }
    }
}
