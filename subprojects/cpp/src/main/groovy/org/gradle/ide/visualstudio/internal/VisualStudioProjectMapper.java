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
import org.gradle.api.Transformer;
import org.gradle.api.specs.Spec;
import org.gradle.nativebinaries.*;
import org.gradle.nativebinaries.internal.ProjectNativeBinaryInternal;
import org.gradle.nativebinaries.internal.ProjectNativeComponentInternal;
import org.gradle.nativebinaries.platform.Platform;
import org.gradle.nativebinaries.platform.PlatformContainer;
import org.gradle.nativebinaries.platform.internal.ArchitectureInternal;
import org.gradle.util.CollectionUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class VisualStudioProjectMapper {
    private final FlavorContainer flavors;
    private final PlatformContainer platforms;

    public VisualStudioProjectMapper(FlavorContainer flavors, PlatformContainer platforms) {
        this.flavors = flavors;
        this.platforms = platforms;
    }

    public ProjectConfigurationNames mapToConfiguration(ProjectNativeBinary nativeBinary) {
        String projectName = makeName(
                getFlavorComponent(nativeBinary),
                getVariantComponent((ProjectNativeBinaryInternal) nativeBinary),
                baseName((ProjectNativeBinaryInternal) nativeBinary),
                projectSuffix(nativeBinary));
        String configurationName = makeName(
                getPlatformComponent(nativeBinary),
                nativeBinary.getBuildType().getName());
        String platformName = getArchitectureName(nativeBinary.getTargetPlatform());
        return new ProjectConfigurationNames(projectName, configurationName, platformName);
    }

    private String baseName(ProjectNativeBinaryInternal nativeBinary) {
        return nativeBinary.getComponent().getBaseName();
    }

    private String getVariantComponent(ProjectNativeBinaryInternal nativeBinary) {
        ComponentVisualStudioProjects componentProjects = new ComponentVisualStudioProjects();
        for (NativeBinary binary : nativeBinary.getComponent().getBinaries().withType(nativeBinary.getClass())) {
            if (binary.getFlavor().equals(nativeBinary.getFlavor())) {
                ProjectNativeBinaryInternal projectBinary = (ProjectNativeBinaryInternal) binary;
                componentProjects.add(projectBinary);
            }
        }
        return componentProjects.getBinaryNameComponent(nativeBinary);
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
        Set<String> architectures = new HashSet<String>();
        for (Platform platform : component.choosePlatforms(platforms)) {
            String architecture = getArchitectureName(platform);
            if (!architectures.add(architecture)) {
                // Duplicate architecture so does not uniquely identify configuration: configuration name must contain platform name
                return nativeBinary.getTargetPlatform().getName();
            }
        }
        return null;
    }

    private String getArchitectureName(Platform targetPlatform) {
        ArchitectureInternal arch = (ArchitectureInternal) targetPlatform.getArchitecture();
        return arch.isIa64() ? "Itanium" : arch.isAmd64() ? "x64" : "Win32";
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

    static class ComponentVisualStudioProjects {
        Set<VisualStudioProjectBinaries> projects = new HashSet<VisualStudioProjectBinaries>();

        void add(ProjectNativeBinaryInternal binary) {
            for (VisualStudioProjectBinaries project : projects) {
                if (project.canContain(binary)) {
                    project.add(binary);
                    return;
                }
            }
            VisualStudioProjectBinaries project = new VisualStudioProjectBinaries();
            project.add(binary);
            projects.add(project);
        }

        String getBinaryNameComponent(final ProjectNativeBinaryInternal binary) {
            // If all binaries have the same project, then just return the component name.
            if (projects.size() == 1) {
                return null;
            }
            VisualStudioProjectBinaries project = findProjectForBinary(binary);
            if (isDifferentiatedByBuildType()) {
                return project.getBuildTypeName();
            }
            if (isDifferentiatedByPlatform()) {
                return project.getPlatformName();
            }
            return project.getFullName();
        }

        private boolean isDifferentiatedByPlatform() {
            Set<String> uniquePlatformNames = CollectionUtils.collect(projects, new Transformer<String, VisualStudioProjectBinaries>() {
                public String transform(VisualStudioProjectBinaries original) {
                    return original.getPlatformName();
                }
            });
            return uniquePlatformNames.size() == projects.size();
        }

        private boolean isDifferentiatedByBuildType() {
            Set<String> uniqueBuildTypes = CollectionUtils.collect(projects, new Transformer<String, VisualStudioProjectBinaries>() {
                public String transform(VisualStudioProjectBinaries original) {
                    return original.getBuildTypeName();
                }
            });
            return uniqueBuildTypes.size() == projects.size();
        }

        private VisualStudioProjectBinaries findProjectForBinary(final ProjectNativeBinaryInternal binary) {
            // Find the project for this binary
            return CollectionUtils.findFirst(projects, new Spec<VisualStudioProjectBinaries>() {
                public boolean isSatisfiedBy(VisualStudioProjectBinaries element) {
                    return element.contains(binary);
                }
            });
        }
    }

    private static class VisualStudioProjectBinaries {
        private final Set<ProjectNativeBinaryInternal> binaries = new HashSet<ProjectNativeBinaryInternal>();

        void add(ProjectNativeBinary binary) {
            binaries.add((ProjectNativeBinaryInternal) binary);
        }

        boolean contains(ProjectNativeBinary binary) {
            return binaries.contains(binary);
        }

        boolean canContain(ProjectNativeBinary candidate) {
            if (binaries.isEmpty()) {
                return true;
            }
            ProjectNativeBinaryInternal prototype = binaries.iterator().next();
            return prototype.getSource().size() == candidate.getSource().size()
                    && prototype.getSource().containsAll(candidate.getSource());
        }

        String getBuildTypeName() {
            List<String> buildTypeNames = CollectionUtils.sort(CollectionUtils.collect(binaries, new Transformer<String, ProjectNativeBinaryInternal>() {
                public String transform(ProjectNativeBinaryInternal original) {
                    return original.getBuildType().getName();
                }
            }));
            return makeName(buildTypeNames);
        }

        String getPlatformName() {
            List<String> platformNames = CollectionUtils.sort(CollectionUtils.collect(binaries, new Transformer<String, ProjectNativeBinaryInternal>() {
                public String transform(ProjectNativeBinaryInternal original) {
                    return original.getTargetPlatform().getName();
                }
            }));
            return makeName(platformNames);
        }

        String getFullName() {
            return makeName(Arrays.asList(getBuildTypeName(), getPlatformName()));
        }
    }
}
