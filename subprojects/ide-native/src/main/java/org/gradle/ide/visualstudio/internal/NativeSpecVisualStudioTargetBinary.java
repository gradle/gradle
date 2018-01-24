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
import com.google.common.collect.Sets;
import org.gradle.api.Buildable;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.Transformer;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.UnionFileCollection;
import org.gradle.api.internal.file.collections.FileCollectionAdapter;
import org.gradle.api.internal.file.collections.MinimalFileSet;
import org.gradle.api.internal.tasks.AbstractTaskDependency;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.nativeplatform.HeaderExportingSourceSet;
import org.gradle.language.rc.WindowsResourceSet;
import org.gradle.nativeplatform.NativeBinarySpec;
import org.gradle.nativeplatform.NativeDependencySet;
import org.gradle.nativeplatform.NativeExecutableBinarySpec;
import org.gradle.nativeplatform.NativeLibraryBinary;
import org.gradle.nativeplatform.PreprocessingTool;
import org.gradle.nativeplatform.SharedLibraryBinarySpec;
import org.gradle.nativeplatform.StaticLibraryBinarySpec;
import org.gradle.nativeplatform.internal.NativeBinarySpecInternal;
import org.gradle.nativeplatform.tasks.InstallExecutable;
import org.gradle.nativeplatform.test.NativeTestSuiteBinarySpec;
import org.gradle.nativeplatform.toolchain.internal.MacroArgsConverter;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.gradle.util.CollectionUtils.collect;
import static org.gradle.util.CollectionUtils.filter;

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

        return new FileCollectionAdapter(new LanguageSourceSetCollectionAdapter(getComponentName() + " source files", binary.getInputs(), filter, transform));
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

        return new FileCollectionAdapter(new LanguageSourceSetCollectionAdapter(getComponentName() + " resource files", binary.getInputs(), filter, transform));
    }

    @Override
    public FileCollection getHeaderFiles() {
        Spec<LanguageSourceSet> filter =  new Spec<LanguageSourceSet>() {
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

        return new FileCollectionAdapter(new LanguageSourceSetCollectionAdapter(getComponentName() + " header files", binary.getInputs(), filter, transform));
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
            return new File(installTask.getInstallDirectory().get().getAsFile(), "lib/" + installTask.getSourceFile().get().getAsFile().getName());
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

    private List<String> getDefines(String tool) {
        PreprocessingTool rcCompiler = findCompiler(tool);
        return rcCompiler == null ? Lists.<String>newArrayList() : new MacroArgsConverter().transform(rcCompiler.getMacros());
    }

    private PreprocessingTool findCompiler(String tool) {
        return (PreprocessingTool) binary.getToolByName(tool);
    }

    @Override
    public List<File> getIncludePaths() {
        Set<File> includes = new LinkedHashSet<File>();

        for (LanguageSourceSet sourceSet : binary.getInputs()) {
            if (sourceSet instanceof HeaderExportingSourceSet) {
                includes.addAll(((HeaderExportingSourceSet) sourceSet).getExportedHeaders().getSrcDirs());
            }
        }

        for (NativeDependencySet lib : binary.getLibs()) {
            includes.addAll(lib.getIncludeRoots().getFiles());
        }

        return new ArrayList<File>(includes);
    }

    @Override
    public Iterable<VisualStudioTargetBinary> getDependencies() {
        Set<VisualStudioTargetBinary> targetBinaries = Sets.newHashSet();
        for (NativeLibraryBinary dependency : binary.getDependentBinaries()) {
            if (dependency instanceof NativeBinarySpecInternal) {
                targetBinaries.add(new NativeSpecVisualStudioTargetBinary((NativeBinarySpec) dependency));
            }
        }
        return targetBinaries;
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

    // TODO: There has to be a simpler way to do this.
    // We want to create a buildable filecollection based on a filtered view of selected source directory sets
    // in the binary inputs.
    private static class LanguageSourceSetCollectionAdapter implements MinimalFileSet, Buildable {
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
        public Set<File> getFiles() {
            Set<LanguageSourceSet> filtered = filter(inputs, filterSpec);
            return new UnionFileCollection(collect(filtered, transformer)).getFiles();
        }

        @Override
        public String getDisplayName() {
            return displayName;
        }

        @Override
        public TaskDependency getBuildDependencies() {
            return new AbstractTaskDependency() {
                @Override
                public void visitDependencies(TaskDependencyResolveContext context) {
                    for (LanguageSourceSet sourceSet : inputs) {
                        context.add(sourceSet);
                    }
                }
            };
        }
    }
}
