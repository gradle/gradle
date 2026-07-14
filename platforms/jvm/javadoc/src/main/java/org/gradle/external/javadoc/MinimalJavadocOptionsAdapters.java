/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.external.javadoc;

import org.gradle.internal.instrumentation.api.annotations.BytecodeUpgrade;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Provides upgrade adapters for {@link MinimalJavadocOptions}.
 */
class MinimalJavadocOptionsAdapters {

    static class DocletpathAdapter {
        @BytecodeUpgrade
        static List<File> getDocletpath(MinimalJavadocOptions self) {
            return new ArrayList<>(self.getDocletpath().getFiles());
        }

    }

    static class ClasspathAdapter {
        @BytecodeUpgrade
        static List<File> getClasspath(MinimalJavadocOptions self) {
            return new ArrayList<>(self.getClasspath().getFiles());
        }

    }

    static class BootclasspathAdapter {
        @BytecodeUpgrade
        static List<File> getBootClasspath(MinimalJavadocOptions self) {
            return new ArrayList<>(self.getBootClasspath().getFiles());
        }

    }

    static class ModulePath {
        @BytecodeUpgrade
        static List<File> getModulePath(MinimalJavadocOptions self) {
            return new ArrayList<>(self.getModulePath().getFiles());
        }

    }

    static class ExtDirsAdapter {
        @BytecodeUpgrade
        static List<File> getExtDirs(MinimalJavadocOptions self) {
            return new ArrayList<>(self.getExtDirs().getFiles());
        }

    }

    static class OptionFilesAdapter {
        @BytecodeUpgrade
        static List<File> getOptionFiles(MinimalJavadocOptions self) {
            return new ArrayList<>(self.getOptionFiles().getFiles());
        }

    }
}
