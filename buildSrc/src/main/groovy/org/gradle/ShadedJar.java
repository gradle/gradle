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
package org.gradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.tasks.*;
import org.gradle.internal.exceptions.Contextual;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import com.google.common.io.ByteStreams;

import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.jar.JarFile;

import java.io.*;
import java.util.*;

@CacheableTask
public class ShadedJar extends DefaultTask {
    private FileCollection sourceFiles;
    private File classesDir;
    private File jarFile;
    private File analysisFile;
    private String shadowPackage;
    private Set<String> keepPackages = new LinkedHashSet<>();
    private Set<String> unshadedPackages = new LinkedHashSet<>();
    private Set<String> ignorePackages = new LinkedHashSet<>();

    /**
     * The directory to write temporary class files to.
     */
    @OutputDirectory
    public File getClassesDir() {
        return classesDir;
    }

    public void setClassesDir(File classesDir) {
        this.classesDir = classesDir;
    }

    /**
     * The output Jar file.
     */
    @OutputFile
    public File getJarFile() {
        return jarFile;
    }

    public void setJarFile(File jarFile) {
        this.jarFile = jarFile;
    }

    /**
     * The package name to prefix all shaded class names with.
     */
    @Input
    public String getShadowPackage() {
        return shadowPackage;
    }

    public void setShadowPackage(String shadowPackage) {
        this.shadowPackage = shadowPackage;
    }

    /**
     * Retain only those classes in the keep package hierarchies, plus any classes that are reachable from these classes.
     */
    @Input
    public Set<String> getKeepPackages() {
        return keepPackages;
    }

    public void setKeepPackages(Set<String> keepPackages) {
        this.keepPackages = keepPackages;
    }

    /**
     * Do not rename classes in the unshaded package hierarchies. Always includes 'java'.
     */
    @Input
    public Set<String> getUnshadedPackages() {
        return unshadedPackages;
    }

    public void setUnshadedPackages(Set<String> unshadedPackages) {
        this.unshadedPackages = unshadedPackages;
    }

    /**
     * Do not retain classes in the ingore packages hierarchies, unless reachable from some other retained class.
     */
    @Input
    public Set<String> getIgnorePackages() {
        return ignorePackages;
    }

    public void setIgnorePackages(Set<String> ignorePackages) {
        this.ignorePackages = ignorePackages;
    }

    /**
     * The source files to generate the jar from.
     */
    @Classpath
    public FileCollection getSourceFiles() {
        return sourceFiles;
    }

    public void setSourceFiles(FileCollection sourceFiles) {
        this.sourceFiles = sourceFiles;
    }

    /**
     * File to write the text analysis report to.
     */
    @OutputFile
    public File getAnalysisFile() {
        return analysisFile;
    }

    public void setAnalysisFile(File analysisFile) {
        this.analysisFile = analysisFile;
    }

    @TaskAction
    public void run() throws Exception {
        long start = System.currentTimeMillis();
        PrintWriter writer = new PrintWriter(analysisFile);
        try {
            final ClassGraph classes = new ClassGraph(new PackagePatterns(keepPackages), new PackagePatterns(unshadedPackages), new PackagePatterns(ignorePackages), shadowPackage);
            List<FileCollection> classFiles = new ArrayList<>();
            for (File sourceFile : sourceFiles) {
                classFiles.add(getProject().zipTree(sourceFile));
            }
            analyse(getProject().files(classFiles), classes, writer);
            writeJar(classes, classesDir, jarFile, writer);
        } finally {
            writer.close();
        }
        long end = System.currentTimeMillis();
        System.out.println("Analysis took " + (end-start) + "ms.");
    }

    private void analyse(FileCollection sourceFiles, final ClassGraph classes, final PrintWriter writer) {
        final PackagePatterns ignored = new PackagePatterns(Collections.singleton("java"));
        sourceFiles.getAsFileTree().visit(new FileVisitor() {
            boolean seenManifest;

            public void visitDir(FileVisitDetails dirDetails) {
            }

            public void visitFile(FileVisitDetails fileDetails) {
                writer.print(fileDetails.getPath() + ": ");
                if (fileDetails.getPath().endsWith(".class")) {
                    try {
                        ClassReader reader;
                        try (InputStream inputStream = new BufferedInputStream(fileDetails.open())) {
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
                        throw new ClassAnalysisException("Could not transform class from " + fileDetails.getFile(), exception);
                    }
                } else if (fileDetails.getPath().endsWith(".properties") && classes.unshadedPackages.matches(fileDetails.getPath())) {
                    writer.println("include");
                    classes.addResource(new ResourceDetails(fileDetails.getPath(), fileDetails.getFile()));
                } else if (fileDetails.getPath().equals(JarFile.MANIFEST_NAME) && !seenManifest) {
                    seenManifest = true;
                    classes.manifest = new ResourceDetails(fileDetails.getPath(), fileDetails.getFile());
                } else {
                    writer.println("skipped");
                }
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
            this.shadowPackagePrefix = shadowPackage.replace('.', '/').concat("/");
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
