/*
 * Copyright 2013 the original author or authors.
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

import org.apache.commons.lang.StringUtils;
import org.gradle.nativeplatform.NativeBinarySpec;
import org.gradle.nativeplatform.NativeExecutableBinarySpec;
import org.gradle.nativeplatform.SharedLibraryBinarySpec;
import org.gradle.nativeplatform.StaticLibraryBinarySpec;
import org.gradle.nativeplatform.internal.NativeBinarySpecInternal;
import org.gradle.nativeplatform.test.NativeTestSuiteBinarySpec;

import java.util.List;

public class VisualStudioProjectMapper {

    public ProjectConfigurationNames mapToConfiguration(NativeBinarySpec nativeBinary) {
        String projectName = projectPrefix(nativeBinary) + componentName(nativeBinary) + projectSuffix(nativeBinary);
        String configurationName = getConfigurationName(nativeBinary);
        return new ProjectConfigurationNames(projectName, configurationName, "Win32");
    }

    private String getConfigurationName(NativeBinarySpec nativeBinary) {
        List<String> dimensions = ((NativeBinarySpecInternal) nativeBinary).getNamingScheme().getVariantDimensions();
        if (dimensions.isEmpty()) {
            return nativeBinary.getBuildType().getName();
        }
        return makeName(dimensions);
    }

    private String projectPrefix(NativeBinarySpec nativeBinary) {
        String projectPath = nativeBinary.getComponent().getProjectPath();
        if (":".equals(projectPath)) {
            return "";
        }
        return projectPath.substring(1).replace(":", "_") + "_";
    }

    private String componentName(NativeBinarySpec nativeBinary) {
        return nativeBinary.getComponent().getName();
    }

    private String projectSuffix(NativeBinarySpec nativeBinary) {
        return nativeBinary instanceof SharedLibraryBinarySpec ? "Dll"
                : nativeBinary instanceof StaticLibraryBinarySpec ? "Lib"
                : nativeBinary instanceof NativeExecutableBinarySpec ? "Exe"
                : nativeBinary instanceof NativeTestSuiteBinarySpec ? "Exe"
                : "";
    }

    private static String makeName(Iterable<String> components) {
        StringBuilder builder = new StringBuilder();
        for (String component : components) {
            if (component != null && component.length() > 0) {
                if (builder.length() == 0) {
                    builder.append(component);
                } else {
                    builder.append(StringUtils.capitalize(component));
                }
            }
        }
        return builder.toString();
    }

    static class ProjectConfigurationNames {
        public final String project;
        public final String configuration;
        public final String platform;

        ProjectConfigurationNames(String project, String configuration, String platform) {
            this.project = project;
            this.configuration = configuration;
            this.platform = platform;
        }
    }
}
