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

package org.gradle.internal.jvm;

import org.gradle.api.file.FileCollection;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.Collection;

/**
 * Detects whether files are Java modules (have module-info or Automatic-Module-Name).
 */
@ServiceScope(Scope.UserHome.class)
public interface JavaModuleDetector {

    FileCollection inferClasspath(boolean inferModulePath, Collection<File> classpath);

    FileCollection inferClasspath(boolean inferModulePath, @Nullable FileCollection classpath);

    FileCollection inferModulePath(boolean inferModulePath, Collection<File> classpath);

    FileCollection inferModulePath(boolean inferModulePath, @Nullable FileCollection classpath);

    boolean isModule(boolean inferModulePath, FileCollection files);

    boolean isModule(boolean inferModulePath, File file);

    static boolean isModuleSource(boolean inferModulePath, Iterable<File> sourcesRoots) {
        if (!inferModulePath) {
            return false;
        }
        for (File srcFolder : sourcesRoots) {
            if (new File(srcFolder, "module-info.java").exists()) {
                return true;
            }
        }
        return false;
    }
}
