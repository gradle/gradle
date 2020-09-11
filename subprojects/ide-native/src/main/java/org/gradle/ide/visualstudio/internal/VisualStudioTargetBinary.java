/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.ide.visualstudio.internal;

import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.util.VersionNumber;

import java.io.File;
import java.util.List;
import java.util.Set;

/**
 * A model-agnostic adapter for binary information
 */
public interface VisualStudioTargetBinary {
    /**
     * Returns the Gradle project path for this binary
     */
    @Input
    String getProjectPath();

    /**
     * Returns the name of the Gradle component associated with this binary
     */
    @Input
    String getComponentName();

    /**
     * Returns the visual studio project name associated with this binary
     */
    @Input
    String getVisualStudioProjectName();

    /**
     * Returns the visual studio project configuration name associated with this binary
     */
    @Input
    String getVisualStudioConfigurationName();

    /**
     * Returns the target Visual Studio version of this binary.
     */
    @Internal
    VersionNumber getVisualStudioVersion();

    /**
     * Returns the target SDK version of this binary.
     */
    @Internal
    VersionNumber getSdkVersion();

    /**
     * Returns the project suffix to use when naming Visual Studio projects
     */
    @Input
    ProjectType getProjectType();

    /**
     * Returns the variant dimensions associated with this binary
     */
    @Input
    List<String> getVariantDimensions();

    /**
     * Returns the source files associated with this binary
     */
    @Internal
    FileCollection getSourceFiles();

    /**
     * Returns the resource files associated with this binary
     */
    @Internal
    FileCollection getResourceFiles();

    /**
     * Returns the header files associated with this binary
     */
    @Internal
    FileCollection getHeaderFiles();

    /**
     * Returns whether or not this binary represents an executable
     */
    @Input
    boolean isExecutable();

    /**
     * Returns a task that can be used to build this binary
     */
    @Input
    String getBuildTaskPath();

    /**
     * Returns a task that can be used to clean the outputs of this binary
     */
    @Input
    String getCleanTaskPath();

    /**
     * Returns whether or not this binary is a debuggable variant
     */
    @Input
    boolean isDebuggable();

    /**
     * Returns the main product of this binary (i.e. executable or library file)
     */
    @Internal
    File getOutputFile();

    /**
     * Returns the compiler definitions that should be used with this binary
     */
    @Input
    List<String> getCompilerDefines();

    /**
     * Returns the language standard of the source for this binary.
     */
    @Input
    LanguageStandard getLanguageStandard();

    /**
     * Returns the include paths that should be used with this binary
     */
    @Internal
    Set<File> getIncludePaths();

    enum ProjectType {
        EXE("Exe"), LIB("Lib"), DLL("Dll"), NONE("");

        private final String suffix;

        ProjectType(String suffix) {
            this.suffix = suffix;
        }

        public String getSuffix() {
            return suffix;
        }
    }

    enum LanguageStandard {
        NONE(""),
        STD_CPP_14("stdcpp14"),
        STD_CPP_17("stdcpp17"),
        STD_CPP_LATEST("stdcpplatest");

        private final String value;

        LanguageStandard(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public static LanguageStandard from(List<String> arguments) {
            return arguments.stream().filter(it -> it.matches("^[-/]std:c\\+\\+.+")).findFirst().map(it -> {
                if (it.endsWith("++14")) {
                    return LanguageStandard.STD_CPP_14;
                } else if (it.endsWith("++17")) {
                    return LanguageStandard.STD_CPP_17;
                } else if (it.endsWith("++latest")) {
                    return LanguageStandard.STD_CPP_LATEST;
                }
                return LanguageStandard.NONE;
            }).orElse(LanguageStandard.NONE);
        }
    }
}
