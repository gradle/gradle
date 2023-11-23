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

import com.google.common.collect.Lists;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.Transformer;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.CompositeFileCollection;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.specs.Spec;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.nativeplatform.HeaderExportingSourceSet;
import org.gradle.language.rc.WindowsResourceSet;
import org.gradle.nativeplatform.NativeBinarySpec;
import org.gradle.nativeplatform.NativeDependencySet;
import org.gradle.nativeplatform.NativeExecutableBinarySpec;
import org.gradle.nativeplatform.PreprocessingTool;
import org.gradle.nativeplatform.SharedLibraryBinarySpec;
import org.gradle.nativeplatform.StaticLibraryBinarySpec;
import org.gradle.nativeplatform.internal.NativeBinarySpecInternal;
import org.gradle.nativeplatform.tasks.InstallExecutable;
import org.gradle.nativeplatform.test.NativeTestSuiteBinarySpec;
import org.gradle.nativeplatform.toolchain.internal.MacroArgsConverter;
import org.gradle.util.internal.CollectionUtils;
import org.gradle.util.internal.VersionNumber;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class NativeSpecVisualStudioTargetBinary implements VisualStudioTargetBinary {
    private final NativeBinarySpecInternal binary;

    public NativeSpecVisualStudioTargetBinary(NativeBinarySpec binary) {
        this.binary = (NativeBinarySpecInternal) binary;
    }

    @Override
    public String getProjectPath() {
        return binary.getProjectPath();
    }

    @Override
    public String getComponentName() {
        return binary.getComponent().getName();
    }

    @Override
    public String getVisualStudioProjectName() {
        return projectPrefix(getProjectPath()) + getComponentName() + getProjectType().getSuffix();
    }

    @Override
    public String getVisualStudioConfigurationName() {
        return makeName(getVariantDimensions());
    }

    @Override
    public VersionNumber getVisualStudioVersion() {
        // Assume VS 2015
        return VersionNumber.parse("14.0");
    }

    @Override
    public VersionNumber getSdkVersion() {
        // Assume 8.1
        return VersionNumber.parse("8.1");
    }

    @Override
    public FileCollection getSourceFiles() {
        Spec<LanguageSourceSet> filter = new Spec<LanguageSourceSet>() {
            @Override
            public boolean isSatisfiedBy(LanguageSourceSet sourceSet) {
                return !(sourceSet instanceof WindowsResourceSet);
            }
        };
        Transformer<FileCollection, LanguageSourceSet> transform = new Transformer<FileCollection, LanguageSourceSet>() {
            @Override
            public FileCollection transform(LanguageSourceSet sourceSet) {
                return sourceSet.getSource();
            }
        };

        return new LanguageSourceSetCollectionAdapter(getComponentName() + " source files", binary.getInputs(), filter, transform);
    }

    @Override
    public FileCollection getResourceFiles() {
        Spec<LanguageSourceSet> filter = new Spec<LanguageSourceSet>() {
            @Override
            public boolean isSatisfiedBy(LanguageSourceSet sourceSet) {
                return sourceSet instanceof WindowsResourceSet;
            }
        };
        Transformer<FileCollection, LanguageSourceSet> transform = new Transformer<FileCollection, LanguageSourceSet>() {
            @Override
            public FileCollection transform(LanguageSourceSet sourceSet) {
                return sourceSet.getSource();
            }
        };

        return new LanguageSourceSetCollectionAdapter(getComponentName() + " resource files", binary.getInputs(), filter, transform);
    }

    @Override
    public FileCollection getHeaderFiles() {
        Spec<LanguageSourceSet> filter = new Spec<LanguageSourceSet>() {
            @Override
            public boolean isSatisfiedBy(LanguageSourceSet sourceSet) {
                return sourceSet instanceof HeaderExportingSourceSet;
            }
        };
        Transformer<FileCollection, LanguageSourceSet> transform = new Transformer<FileCollection, LanguageSourceSet>() {
            @Override
            public FileCollection transform(LanguageSourceSet sourceSet) {
                HeaderExportingSourceSet exportingSourceSet = (HeaderExportingSourceSet) sourceSet;
                return exportingSourceSet.getExportedHeaders().plus(exportingSourceSet.getImplicitHeaders());
            }
        };

        return new LanguageSourceSetCollectionAdapter(getComponentName() + " header files", binary.getInputs(), filter, transform);
    }

    @Override
    public boolean isExecutable() {
        return binary instanceof NativeExecutableBinarySpec || binary instanceof NativeTestSuiteBinarySpec;
    }

    @Override
    public ProjectType getProjectType() {
        return binary instanceof SharedLibraryBinarySpec ? ProjectType.DLL
            : binary instanceof StaticLibraryBinarySpec ? ProjectType.LIB
            : binary instanceof NativeExecutableBinarySpec ? ProjectType.EXE
            : binary instanceof NativeTestSuiteBinarySpec ? ProjectType.EXE
            : ProjectType.NONE;
    }

    @Override
    public List<String> getVariantDimensions() {
        List<String> dimensions = binary.getNamingScheme().getVariantDimensions();
        if (dimensions.isEmpty()) {
            return Lists.newArrayList(binary.getBuildType().getName());
        } else {
            return dimensions;
        }
    }

    private InstallExecutable getInstallTask() {
        final DomainObjectSet<InstallExecutable> installTasks = binary.getTasks().withType(InstallExecutable.class);
        return installTasks.isEmpty() ? null : installTasks.iterator().next();
    }

    @Override
    public String getBuildTaskPath() {
        if (isExecutable()) {
            return getInstallTask().getPath();
        } else {
            return binary.getTasks().getBuild().getPath();
        }
    }

    @Override
    public String getCleanTaskPath() {
        return taskPath("clean");
    }

    private String taskPath(final String taskName) {
        final String projectPath = binary.getComponent().getProjectPath();
        if (":".equals(projectPath)) {
            return ":" + taskName;
        }

        return projectPath + ":" + taskName;
    }

    @Override
    public boolean isDebuggable() {
        return !"release".equals(binary.getBuildType().getName());
    }

    @Override
    public File getOutputFile() {
        if (isExecutable()) {
            InstallExecutable installTask = getInstallTask();
            return new File(installTask.getInstallDirectory().get().getAsFile(), "lib/" + installTask.getExecutableFile().get().getAsFile().getName());
        } else {
            return binary.getPrimaryOutput();
        }
    }

    @Override
    public List<String> getCompilerDefines() {
        List<String> defines = new ArrayList<String>();
        defines.addAll(getDefines("cCompiler"));
        defines.addAll(getDefines("cppCompiler"));
        defines.addAll(getDefines("rcCompiler"));
        return defines;
    }

    @Override
    public LanguageStandard getLanguageStandard() {
        return LanguageStandard.from(binary.getCppCompiler().getArgs());
    }

    private List<String> getDefines(String tool) {
        PreprocessingTool rcCompiler = findCompiler(tool);
        return rcCompiler == null ? Lists.<String>newArrayList() : new MacroArgsConverter().transform(rcCompiler.getMacros());
    }

    private PreprocessingTool findCompiler(String tool) {
        return (PreprocessingTool) binary.getToolByName(tool);
    }

    @Override
    public Set<File> getIncludePaths() {
        Set<File> includes = new LinkedHashSet<File>();

        for (LanguageSourceSet sourceSet : binary.getInputs()) {
            if (sourceSet instanceof HeaderExportingSourceSet) {
                includes.addAll(((HeaderExportingSourceSet) sourceSet).getExportedHeaders().getSrcDirs());
            }
        }

        for (NativeDependencySet lib : binary.getLibs()) {
            includes.addAll(lib.getIncludeRoots().getFiles());
        }

        return includes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        NativeSpecVisualStudioTargetBinary that = (NativeSpecVisualStudioTargetBinary) o;

        return binary.equals(that.binary);
    }

    @Override
    public int hashCode() {
        return binary.hashCode();
    }

    static String projectPrefix(String projectPath) {
        if (":".equals(projectPath)) {
            return "";
        }
        return projectPath.substring(1).replace(":", "_") + "_";
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

    private static class LanguageSourceSetCollectionAdapter extends CompositeFileCollection {
        private final String displayName;
        private final Set<LanguageSourceSet> inputs;
        private final Spec<LanguageSourceSet> filterSpec;
        private final Transformer<FileCollection, LanguageSourceSet> transformer;

        public LanguageSourceSetCollectionAdapter(String displayName, Set<LanguageSourceSet> inputs, Spec<LanguageSourceSet> filterSpec, Transformer<FileCollection, LanguageSourceSet> transformer) {
            this.displayName = displayName;
            this.inputs = inputs;
            this.filterSpec = filterSpec;
            this.transformer = transformer;
        }

        @Override
        public void visitDependencies(TaskDependencyResolveContext context) {
            for (LanguageSourceSet input : inputs) {
                context.add(input);
            }
        }

        @Override
        protected void visitChildren(Consumer<FileCollectionInternal> visitor) {
            Set<LanguageSourceSet> filtered = CollectionUtils.filter(inputs, filterSpec);
            for (LanguageSourceSet languageSourceSet : filtered) {
                visitor.accept((FileCollectionInternal) transformer.transform(languageSourceSet));
            }
        }

        @Override
        public String getDisplayName() {
            return displayName;
        }
    }
}
