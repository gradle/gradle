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

package org.gradle.api.internal.tasks.compile;

import org.gradle.api.internal.tasks.compile.processing.AnnotationProcessorDeclaration;
import org.gradle.api.tasks.compile.CompileOptions;

import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.google.common.collect.ImmutableList.toImmutableList;

public class DefaultJavaCompileSpec extends DefaultJvmLanguageCompileSpec implements JavaCompileSpec {
    private MinimalJavaCompileOptions compileOptions;
    private List<File> annotationProcessorPath;
    private Set<AnnotationProcessorDeclaration> effectiveAnnotationProcessors;
    private Set<String> classesToProcess;
    private List<File> modulePath;
    private List<File> sourceRoots;
    private Set<String> classesToCompile = Collections.emptySet();
    private File backupDestinationDir;

    @Override
    public MinimalJavaCompileOptions getCompileOptions() {
        return compileOptions;
    }

    @Nullable
    @Override
    public File getClassBackupDir() {
        return backupDestinationDir;
    }

    @Override
    public void setClassBackupDir(@Nullable File classBackupDir) {
        this.backupDestinationDir = classBackupDir;
    }

    public void setCompileOptions(CompileOptions compileOptions) {
        this.compileOptions = new MinimalJavaCompileOptions(compileOptions);
    }

    @Override
    public List<File> getAnnotationProcessorPath() {
        return annotationProcessorPath;
    }

    @Override
    public void setAnnotationProcessorPath(List<File> annotationProcessorPath) {
        this.annotationProcessorPath = annotationProcessorPath;
    }

    @Override
    public Set<AnnotationProcessorDeclaration> getEffectiveAnnotationProcessors() {
        return effectiveAnnotationProcessors;
    }

    @Override
    public void setEffectiveAnnotationProcessors(Set<AnnotationProcessorDeclaration> annotationProcessors) {
        this.effectiveAnnotationProcessors = annotationProcessors;
    }

    @Override
    public Set<String> getClassesToProcess() {
        return classesToProcess;
    }

    @Override
    public void setClassesToProcess(Set<String> classes) {
        this.classesToProcess = classes;
    }

    @Override
    public void setClassesToCompile(Set<String> classes) {
        this.classesToCompile = classes;
    }

    @Override
    public Set<String> getClassesToCompile() {
        return classesToCompile;
    }

    @Override
    public List<File> getModulePath() {
        if (modulePath == null || modulePath.isEmpty()) {
            // This is kept for backward compatibility - may be removed in the future
            int i = 0;
            List<String> modulePaths = new ArrayList<>();
            // Some arguments can also be a GString, that is why use Object.toString()
            for (Object argObj : compileOptions.getCompilerArgs()) {
                String arg = argObj.toString();
                if ((arg.equals("--module-path") || arg.equals("-p")) && (i + 1) < compileOptions.getCompilerArgs().size()) {
                    Object argValue = compileOptions.getCompilerArgs().get(++i);
                    String[] modules = argValue.toString().split(File.pathSeparator);
                    modulePaths.addAll(Arrays.asList(modules));
                } else if (arg.startsWith("--module-path=")) {
                    String[] modules = arg.replace("--module-path=", "").split(File.pathSeparator);
                    modulePaths.addAll(Arrays.asList(modules));
                }
                i++;
            }
            modulePath = modulePaths.stream()
                .map(File::new)
                .collect(toImmutableList());
        }
        return modulePath;
    }

    @Override
    public void setModulePath(List<File> modulePath) {
        this.modulePath = modulePath;
    }

    @Override
    public List<File> getSourceRoots() {
        return sourceRoots;
    }

    @Override
    public void setSourcesRoots(List<File> sourcesRoots) {
        this.sourceRoots = sourcesRoots;
    }
}
