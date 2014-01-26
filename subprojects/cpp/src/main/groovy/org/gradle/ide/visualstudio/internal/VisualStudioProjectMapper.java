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
import org.gradle.nativebinaries.FlavorContainer;
import org.gradle.nativebinaries.ProjectNativeBinary;
import org.gradle.nativebinaries.SharedLibraryBinary;
import org.gradle.nativebinaries.StaticLibraryBinary;
import org.gradle.nativebinaries.internal.ProjectNativeComponentInternal;
import org.gradle.nativebinaries.platform.PlatformContainer;

import java.util.Arrays;

public class VisualStudioProjectMapper {
    private final FlavorContainer flavors;
    private final PlatformContainer platforms;

    public VisualStudioProjectMapper(FlavorContainer flavors, PlatformContainer platforms) {
        this.flavors = flavors;
        this.platforms = platforms;
    }

    public ProjectConfigurationNames mapToConfiguration(ProjectNativeBinary nativeBinary) {
        String projectName = baseName(nativeBinary) + projectSuffix(nativeBinary);
        String configurationName = makeName(
                getFlavorComponent(nativeBinary),
                nativeBinary.getBuildType().getName(),
                getPlatformComponent(nativeBinary)
        );
        return new ProjectConfigurationNames(projectName, configurationName, "Win32");
    }

    private String baseName(ProjectNativeBinary nativeBinary) {
        return nativeBinary.getComponent().getBaseName();
    }

    private String projectSuffix(ProjectNativeBinary nativeBinary) {
        return nativeBinary instanceof StaticLibraryBinary ? "Lib"
                : nativeBinary instanceof SharedLibraryBinary ? "Dll"
                : "Exe";
    }

    private static String makeName(String... components) {
        return makeName(Arrays.asList(components));
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

    private String getFlavorComponent(ProjectNativeBinary nativeBinary) {
        ProjectNativeComponentInternal component = (ProjectNativeComponentInternal) nativeBinary.getComponent();
        if (component.chooseFlavors(flavors).size() > 1) {
            return nativeBinary.getFlavor().getName();
        }
        return null;
    }

    private String getPlatformComponent(ProjectNativeBinary nativeBinary) {
        ProjectNativeComponentInternal component = (ProjectNativeComponentInternal) nativeBinary.getComponent();
        if (component.choosePlatforms(platforms).size() > 1) {
            return nativeBinary.getTargetPlatform().getName();
        }
        return null;
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
