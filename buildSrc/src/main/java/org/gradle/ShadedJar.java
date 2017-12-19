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
import org.gradle.api.tasks.*;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Set;

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
        new ShadedJarCreator(sourceFiles, jarFile, analysisFile, classesDir, shadowPackage, keepPackages, unshadedPackages, ignorePackages).createJar();
    }
}
