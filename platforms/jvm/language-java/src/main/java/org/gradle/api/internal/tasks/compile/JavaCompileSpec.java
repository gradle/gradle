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

import javax.annotation.Nullable;
import java.io.File;
import java.util.List;
import java.util.Set;

public interface JavaCompileSpec extends JvmLanguageCompileSpec {

    MinimalJavaCompileOptions getCompileOptions();

    @Override
    File getDestinationDir();

    @Nullable
    File getClassBackupDir();

    void setClassBackupDir(@Nullable File classBackupDir);

    /**
     * The annotation processor path to use. When empty, no processing should be done. When not empty, processing should be done.
     */
    List<File> getAnnotationProcessorPath();

    void setAnnotationProcessorPath(List<File> path);

    void setEffectiveAnnotationProcessors(Set<AnnotationProcessorDeclaration> annotationProcessors);

    Set<AnnotationProcessorDeclaration> getEffectiveAnnotationProcessors();

    void setClassesToProcess(Set<String> classes);

    /**
     * Classes to process are already compiled classes that are passed to Java compiler.
     * They are passed to Java compiler since they are required by some annotation processor to revisit.
     */
    Set<String> getClassesToProcess();

    void setClassesToCompile(Set<String> classes);

    /**
     * Classes to compile are all classes that we know from Java sources that will be compiled.
     * These classes are deleted before a compilation and are not passed to Java compiler (their sources are passed to a compiler).
     * We only need them in {@link CompilationClassBackupService} so we know what files don't need a backup.
     */
    Set<String> getClassesToCompile();

    List<File> getModulePath();

    void setModulePath(List<File> modulePath);

    default boolean annotationProcessingConfigured() {
        return !getAnnotationProcessorPath().isEmpty() && !getCompileOptions().getCompilerArgs().contains("-proc:none");
    }
}
