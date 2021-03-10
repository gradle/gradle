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
package org.gradle.api.internal.catalog;

import com.google.common.collect.Lists;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.logging.text.TreeFormatter;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

class SimpleGeneratedJavaClassCompiler {
    /**
     * Compiles generated Java source files.
     *
     * @param srcDir where the compiler will output the sources
     * @param dstDir where the compiler will output the class files
     * @param classes the classes to compile
     * @param classPath the classpath to use for compilation
     */
    public static void compile(File srcDir, File dstDir, List<ClassSource> classes, ClassPath classPath) throws GeneratedClassCompilationException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> ds = new DiagnosticCollector<>();
        try (StandardJavaFileManager mgr = compiler.getStandardFileManager(ds, null, null)) {
            List<String> options = buildOptions(dstDir, classPath);
            List<File> filesToCompile = outputSourceFilesToSourceDir(srcDir, classes);
            if (dstDir.exists() || dstDir.mkdirs()) {
                Iterable<? extends JavaFileObject> sources = mgr.getJavaFileObjectsFromFiles(filesToCompile);
                JavaCompiler.CompilationTask task = compiler.getTask(null, mgr, ds, options, null, sources);
                task.call();
            } else {
                throw new GeneratedClassCompilationException("Unable to create output classes directory");
            }
        } catch (IOException e) {
            throw new GeneratedClassCompilationException("Unable to compile generated classes", e);
        }
        List<Diagnostic<? extends JavaFileObject>> diagnostics = ds.getDiagnostics().stream()
            .filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
            .collect(Collectors.toList());
        if (!diagnostics.isEmpty()) {
            throwCompilationError(diagnostics);
        }
    }

    private static void throwCompilationError(List<Diagnostic<? extends JavaFileObject>> diagnostics) {
        TreeFormatter formatter = new TreeFormatter();
        formatter.node("Unable to compile generated sources");
        formatter.startChildren();
        for (Diagnostic<? extends JavaFileObject> d : diagnostics) {
            JavaFileObject source = d.getSource();
            String srcFile = source == null ? "unknown" : new File(source.toUri()).getName();
            String diagLine = String.format("File %s, line: %d, %s", srcFile, d.getLineNumber(), d.getMessage(null));
            formatter.node(diagLine);
        }
        formatter.endChildren();
        throw new GeneratedClassCompilationException(formatter.toString());
    }

    private static List<File> outputSourceFilesToSourceDir(File srcDir, List<ClassSource> classes) throws IOException {
        List<File> filesToCompile = Lists.newArrayListWithCapacity(classes.size());
        for (ClassSource classSource : classes) {
            String packageName = classSource.getPackageName();
            String className = classSource.getSimpleClassName();
            String classCode = classSource.getSource();
            File file = sourceFile(srcDir, packageName, className);
            writeSourceFile(classCode, file);
            filesToCompile.add(file);
        }
        return filesToCompile;
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
        options.add("-source");
        options.add("1.8");
        options.add("-target");
        options.add("1.8");
        options.add("-classpath");
        String cp = classPath.getAsFiles().stream().map(File::getAbsolutePath).collect(Collectors.joining(File.pathSeparator));
        options.add(cp);
        options.add("-d");
        options.add(dstDir.getAbsolutePath());
        return options;
    }
}
