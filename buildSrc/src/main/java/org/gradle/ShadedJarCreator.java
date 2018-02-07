/*
 * Copyright 2017 the original author or authors.
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

package org.gradle;

import com.google.common.io.ByteStreams;
import org.gradle.internal.exceptions.Contextual;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

class ShadedJarCreator {

    private final Iterable<File> sourceJars;
    private final File classesDir;
    private final File jarFile;
    private final File analysisFile;
    private final String shadowPackage;
    private final Set<String> keepPackages;
    private final Set<String> unshadedPackages;
    private final Set<String> ignorePackages;

    ShadedJarCreator(Iterable<File> sourceJars, File jarFile, File analysisFile, File classesDir, String shadowPackage, Set<String> keepPackages, Set<String> unshadedPackages, Set<String> ignorePackages) {
        this.sourceJars = sourceJars;
        this.classesDir = classesDir;
        this.jarFile = jarFile;
        this.analysisFile = analysisFile;
        this.shadowPackage = shadowPackage;
        this.keepPackages = keepPackages;
        this.unshadedPackages = unshadedPackages;
        this.ignorePackages = ignorePackages;
    }

    public void createJar() {
        long start = System.currentTimeMillis();
        PrintWriter writer = null;
        try {
            writer = new PrintWriter(analysisFile);
            final ClassGraph classes = new ClassGraph(new PackagePatterns(keepPackages), new PackagePatterns(unshadedPackages), new PackagePatterns(ignorePackages), shadowPackage);
            analyse(classes, writer);
            writeJar(classes, classesDir, jarFile, writer);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
        long end = System.currentTimeMillis();
        System.out.println("Analysis took " + (end - start) + "ms.");
    }

    private void analyse(final ClassGraph classes, final PrintWriter writer) throws IOException {
        final PackagePatterns ignored = new PackagePatterns(Collections.singleton("java"));

        for (File sourceJar : sourceJars) {
            try (FileSystem jarFileSystem = FileSystems.newFileSystem(URI.create("jar:" + sourceJar.toPath().toUri()), new HashMap<String, Object>())) {
                for (Path dir : jarFileSystem.getRootDirectories()) {
                    visitClassDirectory(dir, classes, ignored, writer);
                }
            }
        }
    }

    private void visitClassDirectory(final Path dir, final ClassGraph classes, final PackagePatterns ignored, final PrintWriter writer) throws IOException {
        Files.walkFileTree(dir, new FileVisitor<Path>() {
            boolean seenManifest;

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                writer.print(file.getFileName().toString() + ": ");
                if (file.toString().endsWith(".class")) {
                    try {
                        ClassReader reader;
                        try (InputStream inputStream = Files.newInputStream(file)) {
                            reader = new ClassReader(inputStream);
                        }
                        final ClassDetails details = classes.get(reader.getClassName());
                        details.visited = true;
                        ClassWriter classWriter = new ClassWriter(0);
                        reader.accept(new ClassRemapper(classWriter, new Remapper() {
                            public String map(String name) {
                                if (ignored.matches(name)) {
                                    return name;
                                }
                                ClassDetails dependencyDetails = classes.get(name);
                                if (dependencyDetails != details) {
                                    details.dependencies.add(dependencyDetails);
                                }
                                return dependencyDetails.outputClassName;
                            }
                        }), ClassReader.EXPAND_FRAMES);

                        writer.println("mapped class name: " + details.outputClassName);
                        File outputFile = new File(classesDir, details.outputClassName.concat(".class"));
                        outputFile.getParentFile().mkdirs();
                        try (OutputStream outputStream = new FileOutputStream(outputFile)) {
                            outputStream.write(classWriter.toByteArray());
                        }
                    } catch (Exception exception) {
                        throw new ClassAnalysisException("Could not transform class from " + file.toFile(), exception);
                    }
                } else if (file.toString().endsWith(".properties") && classes.unshadedPackages.matches(file.toString())) {
                    writer.println("include");
                    classes.addResource(new ResourceDetails(file.toString(), file.toFile()));
                } else if (file.toString().equals(JarFile.MANIFEST_NAME) && !seenManifest) {
                    seenManifest = true;
                    classes.manifest = new ResourceDetails(file.toString(), file.toFile());
                } else {
                    writer.println("skipped");
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                return FileVisitResult.TERMINATE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void writeJar(ClassGraph classes, File classesDir, File jarFile, PrintWriter writer) {
        try {
            writer.println();
            writer.println("CLASS GRAPH");
            writer.println();
            try (OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(jarFile))) {
                JarOutputStream jarOutputStream = new JarOutputStream(outputStream);
                if (classes.manifest != null) {
                    addJarEntry(classes.manifest.resourceName, classes.manifest.sourceFile, jarOutputStream);
                }
                Set<ClassDetails> visited = new LinkedHashSet<>();
                for (ClassDetails classDetails : classes.entryPoints) {
                    visitTree(classDetails, classesDir, jarOutputStream, writer, "- ", visited);
                }
                for (ResourceDetails resource : classes.resources) {
                    addJarEntry(resource.resourceName, resource.sourceFile, jarOutputStream);
                }
                jarOutputStream.close();
            }
        } catch (Exception exception) {
            throw new ClassAnalysisException("Could not write shaded Jar " + jarFile, exception);
        }
    }

    private void visitTree(ClassDetails classDetails, File classesDir, JarOutputStream jarOutputStream, PrintWriter writer, String prefix, Set<ClassDetails> visited) throws IOException {
        if (!visited.add(classDetails)) {
            return;
        }
        if (classDetails.visited) {
            writer.println(prefix + classDetails.className);
            String fileName = classDetails.outputClassName.concat(".class");
            File classFile = new File(classesDir, fileName);
            addJarEntry(fileName, classFile, jarOutputStream);
            for (ClassDetails dependency : classDetails.dependencies) {
                String childPrefix = "  " + prefix;
                visitTree(dependency, classesDir, jarOutputStream, writer, childPrefix, visited);
            }
        } else {
            writer.println(prefix + classDetails.className + " (not included)");
        }
    }

    private void addJarEntry(String entryName, File sourceFile, JarOutputStream jarOutputStream) throws IOException {
        jarOutputStream.putNextEntry(new ZipEntry(entryName));
        try (InputStream inputStream = new BufferedInputStream(new FileInputStream(sourceFile))) {
            ByteStreams.copy(inputStream, jarOutputStream);
        }
        jarOutputStream.closeEntry();
    }

    private static class ClassGraph {
        final Map<String, ClassDetails> classes = new LinkedHashMap<>();
        final Set<ClassDetails> entryPoints = new LinkedHashSet<>();
        final Set<ResourceDetails> resources = new LinkedHashSet<>();
        ResourceDetails manifest;
        final PackagePatterns unshadedPackages;
        final PackagePatterns ignorePackages;
        final PackagePatterns keepPackages;
        final String shadowPackagePrefix;

        public ClassGraph(PackagePatterns keepPackages, PackagePatterns unshadedPackages, PackagePatterns ignorePackages, String shadowPackage) {
            this.keepPackages = keepPackages;
            this.unshadedPackages = unshadedPackages;
            this.ignorePackages = ignorePackages;
            this.shadowPackagePrefix = shadowPackage == null ? "" : shadowPackage.replace('.', '/').concat("/");
        }

        public void addResource(ResourceDetails resource) {
            resources.add(resource);
        }

        public ClassDetails get(String className) {
            ClassDetails classDetails = classes.get(className);
            if (classDetails == null) {
                classDetails = new ClassDetails(className, unshadedPackages.matches(className) ? className : shadowPackagePrefix + className);
                classes.put(className, classDetails);
                if (keepPackages.matches(className) && !ignorePackages.matches(className)) {
                    entryPoints.add(classDetails);
                }
            }
            return classDetails;
        }
    }

    private static class ResourceDetails {
        final String resourceName;
        final File sourceFile;

        public ResourceDetails(String resourceName, File sourceFile) {
            this.resourceName = resourceName;
            this.sourceFile = sourceFile;
        }
    }

    private static class ClassDetails {
        final String className;
        final String outputClassName;
        boolean visited;
        final Set<ClassDetails> dependencies = new LinkedHashSet<>();

        public ClassDetails(String className, String outputClassName) {
            this.className = className;
            this.outputClassName = outputClassName;
        }
    }

    private static class PackagePatterns {
        private final Set<String> prefixes = new HashSet<>();
        private final Set<String> names = new HashSet<>();

        public PackagePatterns(Set<String> prefixes) {
            for (String prefix : prefixes) {
                String internalName = prefix.replace('.', '/');
                this.names.add(internalName);
                this.prefixes.add(internalName + "/");
            }
        }

        public boolean matches(String packageName) {
            if (names.contains(packageName)) {
                return true;
            }
            for (String prefix : prefixes) {
                if (packageName.startsWith(prefix)) {
                    names.add(packageName);
                    return true;
                }
            }
            return false;
        }
    }

    @Contextual
    public static class ClassAnalysisException extends RuntimeException {
        public ClassAnalysisException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
