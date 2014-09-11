/*
 * Copyright 2011 the original author or authors.
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

import org.gradle.api.internal.tasks.SimpleWorkResult;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.internal.jvm.Jvm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.List;

public class JdkJavaCompiler implements Compiler<JavaCompileSpec>, Serializable {
    private static final Logger LOGGER = LoggerFactory.getLogger(JdkJavaCompiler.class);

    public WorkResult execute(JavaCompileSpec spec) {
        LOGGER.info("Compiling with JDK Java compiler API.");

        JavaCompiler.CompilationTask task = createCompileTask(spec);
        boolean success = task.call();
        if (!success) {
            throw new CompilationFailedException();
        }

        return new SimpleWorkResult(true);
    }

    private JavaCompiler.CompilationTask createCompileTask(JavaCompileSpec spec) {
        List<String> options = new JavaCompilerArgumentsBuilder(spec).build();
        JavaCompiler compiler = findCompiler();
        if(compiler==null){
            throw new RuntimeException("Cannot find System Java Compiler. Ensure that you have installed a JDK (not just a JRE) and configured your JAVA_HOME system variable to point to the according directory.");
        }
        CompileOptions compileOptions = spec.getCompileOptions();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, compileOptions.getEncoding() != null ? Charset.forName(compileOptions.getEncoding()) : null);
        Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromFiles(spec.getSource());
        return compiler.getTask(null, null, null, options, null, compilationUnits);
    }

    private static JavaCompiler findCompiler() {
        File realJavaHome = Jvm.current().getJavaHome();
        File javaHomeFromToolProvidersPointOfView = new File(System.getProperty("java.home"));
        if (realJavaHome.equals(javaHomeFromToolProvidersPointOfView)) {
            return ToolProvider.getSystemJavaCompiler();
        }

        System.setProperty("java.home", realJavaHome.getAbsolutePath());
        try {
            return ToolProvider.getSystemJavaCompiler();
        } finally {
            System.setProperty("java.home", javaHomeFromToolProvidersPointOfView.getAbsolutePath());
        }
    }
}
