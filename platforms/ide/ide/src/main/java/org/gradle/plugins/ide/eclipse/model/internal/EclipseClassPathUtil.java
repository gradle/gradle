/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.plugins.ide.eclipse.model.internal;

import org.gradle.api.NonNullApi;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.internal.file.FileTreeInternal;
import org.gradle.api.internal.tasks.JvmConstants;
import org.gradle.api.internal.tasks.compile.CompilationSourceDirs;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.internal.jvm.JavaModuleDetector;

import java.io.File;
import java.util.List;

@NonNullApi
public class EclipseClassPathUtil {

    public static boolean isInferModulePath(Project project) {
        Task javaCompileTask = project.getTasks().findByName(JvmConstants.COMPILE_JAVA_TASK_NAME);
        if (javaCompileTask instanceof JavaCompile) {
            JavaCompile javaCompile = (JavaCompile) javaCompileTask;
            if (javaCompile.getModularity().getInferModulePath().get()) {
                List<File> sourceRoots = CompilationSourceDirs.inferSourceRoots((FileTreeInternal) javaCompile.getSource());
                return JavaModuleDetector.isModuleSource(true, sourceRoots);
            }
        }
        return false;
    }
}
