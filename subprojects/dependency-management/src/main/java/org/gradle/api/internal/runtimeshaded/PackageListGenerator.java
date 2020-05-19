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
package org.gradle.api.internal.runtimeshaded;

import org.gradle.api.DefaultTask;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.ErroringAction;
import org.gradle.internal.IoActions;
import org.gradle.internal.util.Trie;

import javax.inject.Inject;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * This task will generate the list of relocated packages into a file that will in turn be used when generating the runtime shaded jars. All we need is a list of packages that need to be relocated, so
 * we'll make sure to filter the list of packages before generating the file.
 *
 * It is assumed that the layout of the directories follow the JVM conventions. This allows us to effectively skip opening the class files to determine the real package name.
 */
@CacheableTask
public class PackageListGenerator extends DefaultTask {
    public static final List<String> DEFAULT_EXCLUDES = Arrays.asList(
        "org/gradle",
        "java",
        "javax/annotation",
        "javax/inject",
        "javax/xml",
        "kotlin",
        "groovy",
        "groovyjarjarantlr",
        "net/rubygrapefruit",
        "org/codehaus/groovy",
        "org/apache/tools/ant",
        "org/apache/commons/logging",
        "org/slf4j",
        "org/apache/log4j",
        "org/apache/xerces",
        "org/w3c/dom",
        "org/xml/sax",
        "sun/misc");

    private File outputFile;
    private FileCollection classpath;
    private List<String> excludes;

    public PackageListGenerator() {
        excludes = DEFAULT_EXCLUDES;
    }

    @Classpath
    public FileCollection getClasspath() {
        return classpath;
    }

    public void setClasspath(FileCollection classpath) {
        this.classpath = classpath;
    }

    @OutputFile
    public File getOutputFile() {
        return outputFile;
    }

    public void setOutputFile(File outputFile) {
        this.outputFile = outputFile;
    }

    @Input
    public List<String> getExcludes() {
        return excludes;
    }

    public void setExcludes(List<String> excludes) {
        this.excludes = excludes;
    }

    @Inject
    protected DirectoryFileTreeFactory getDirectoryFileTreeFactory() {
        throw new UnsupportedOperationException();
    }

    @TaskAction
    public void generate() {
        IoActions.writeTextFile(getOutputFile(), new ErroringAction<BufferedWriter>() {
            @Override
            public void doExecute(final BufferedWriter bufferedWriter) throws Exception {
                Trie packages = collectPackages();
                packages.dump(false, new ErroringAction<String>() {
                    @Override
                    public void doExecute(String s) throws Exception {
                        bufferedWriter.write(s);
                        bufferedWriter.newLine();
                    }
                });
            }
        });
    }

    private Trie collectPackages() throws IOException {
        Trie.Builder builder = new Trie.Builder();
        for (File file : getClasspath()) {
            if (file.exists()) {
                if (file.getName().endsWith(".jar")) {
                    processJarFile(file, builder);
                } else {
                    processDirectory(file, builder);
                }
            }
        }

        return builder.build();
    }

    private void processDirectory(File file, final Trie.Builder builder) {
        getDirectoryFileTreeFactory().create(file).visit(new FileVisitor() {
            @Override
            public void visitDir(FileVisitDetails dirDetails) {
            }

            @Override
            public void visitFile(FileVisitDetails fileDetails) {
                try {
                    ZipEntry zipEntry = new ZipEntry(fileDetails.getPath());
                    processEntry(zipEntry, builder);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        });
    }

    private void processJarFile(File file, final Trie.Builder builder) throws IOException {
        IoActions.withResource(openJarFile(file), new ErroringAction<ZipInputStream>() {
            @Override
            protected void doExecute(ZipInputStream inputStream) throws Exception {
                ZipEntry zipEntry = inputStream.getNextEntry();
                while (zipEntry != null) {
                    processEntry(zipEntry, builder);
                    zipEntry = inputStream.getNextEntry();
                }
            }
        });
    }

    private void processEntry(ZipEntry zipEntry, Trie.Builder builder) throws IOException {
        String name = zipEntry.getName();
        if (name.endsWith(".class")) {
            processClassFile(zipEntry, builder);
        }
    }

    private void processClassFile(ZipEntry zipEntry, Trie.Builder builder) throws IOException {
        int endIndex = zipEntry.getName().lastIndexOf("/");
        if (endIndex > 0) {
            String packageName = zipEntry.getName().substring(0, endIndex);
            for (String exclude : getExcludes()) {
                if ((packageName + "/").startsWith(exclude + "/")) {
                    return;
                }
            }
            builder.addWord(packageName);
        }
    }

    private static ZipInputStream openJarFile(File file) throws IOException {
        return new ZipInputStream(new FileInputStream(file));
    }

}
