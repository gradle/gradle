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

import java.io.File;
import java.util.List;
import java.util.Set;

public interface JavaCompileSpec extends JvmLanguageCompileSpec {
    MinimalJavaCompileOptions getCompileOptions();

    @Override
    File getDestinationDir();

    /**
     * The annotation processor path to use. When empty, no processing should be done. When not empty, processing should be done.
     */
    List<File> getAnnotationProcessorPath();

    void setAnnotationProcessorPath(List<File> path);

    void setEffectiveAnnotationProcessors(Set<AnnotationProcessorDeclaration> annotationProcessors);

    Set<AnnotationProcessorDeclaration> getEffectiveAnnotationProcessors();

    void setClasses(Set<String> classes);

    Set<String> getClasses();

    List<File> getModulePath();

    void setModulePath(List<File> modulePath);

    default boolean annotationProcessingConfigured() {
        return !getAnnotationProcessorPath().isEmpty() && !getCompileOptions().getCompilerArgs().contains("-proc:none");
    }
}
