/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.compiler.java;

import org.gradle.internal.compiler.java.listeners.constants.ConstantDependentsConsumer;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.nio.charset.StandardCharsets.UTF_8;

public class TestCompiler {

    private final File outputFolder;
    private final Function<File, Optional<String>> relativize;
    private final Consumer<String> classBackupService;
    private final Consumer<Map<String, Set<String>>> classNameConsumer;
    private final ConstantDependentsConsumer constantDependentsConsumer;

    public TestCompiler(File outputFolder,
                        Function<File, Optional<String>> relativize,
                        Consumer<String> classBackupService,
                        Consumer<Map<String, Set<String>>> classNamesConsumer,
                        ConstantDependentsConsumer constantDependentsConsumer) {
        this.outputFolder = outputFolder;
        this.relativize = relativize;
        this.classBackupService = classBackupService;
        this.classNameConsumer = classNamesConsumer;
        this.constantDependentsConsumer = constantDependentsConsumer;
    }

    public void compile(List<File> sourceFiles) {
        StringWriter output = new StringWriter();
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, UTF_8);
        Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromFiles(sourceFiles);
        List<String> arguments = Arrays.asList("-d", outputFolder.getAbsolutePath());
        JavaCompiler.CompilationTask delegate = compiler.getTask(output, fileManager, null, arguments, null, compilationUnits);
        IncrementalCompileTask task = new IncrementalCompileTask(delegate, relativize, classBackupService, classNameConsumer, constantDependentsConsumer::consumeAccessibleDependent, constantDependentsConsumer::consumePrivateDependent);
        if (!task.call()) {
            throw new RuntimeException(output.toString());
        }
    }

}
