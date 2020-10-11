/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.api.internal.std;

import org.gradle.internal.classpath.ClassPath;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class DependenciesClassGenerator {
    public static void compile(File srcDir, File dstDir, String packageName, String className, String classCode, ClassPath classPath) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> ds = new DiagnosticCollector<>();
        try (StandardJavaFileManager mgr = compiler.getStandardFileManager(ds, null, null)) {
            List<String> options = buildOptions(dstDir, classPath);
            File file = sourceFile(srcDir, packageName, className);
            writeSourceFile(classCode, file);
            Iterable<? extends JavaFileObject> sources =
                mgr.getJavaFileObjectsFromFiles(Collections.singletonList(file));
            JavaCompiler.CompilationTask task =
                compiler.getTask(null, mgr, ds, options,
                    null, sources);
            task.call();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        for (Diagnostic<? extends JavaFileObject> d : ds.getDiagnostics()) {
            System.out.format("Line: %d, %s in %s",
                d.getLineNumber(), d.getMessage(null),
                d.getSource().getName());
        }
    }

    private static void writeSourceFile(String classCode, File file) throws IOException {
        file.getParentFile().mkdirs();
        Files.write(file.toPath(), classCode.getBytes(StandardCharsets.UTF_8));
    }

    private static File sourceFile(File srcDir, String packageName, String className) {
        return new File(srcDir, packageName.replace('.', '/') + "/" + className + ".java");
    }

    private static List<String> buildOptions(File dstDir, ClassPath classPath) {
        List<String> options = new ArrayList<>();
        options.add("-classpath");
        String cp = classPath.getAsFiles().stream().map(File::getAbsolutePath).collect(Collectors.joining(":"));
        options.add(cp);
        options.add("-d");
        options.add(dstDir.getAbsolutePath());
        return options;
    }
}
