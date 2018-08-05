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

package org.gradle.language.cpp.internal.tooling;

import com.google.common.collect.ImmutableList;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.RegularFile;
import org.gradle.api.internal.tasks.TaskDependencyContainer;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.provider.Provider;
import org.gradle.language.cpp.CppApplication;
import org.gradle.language.cpp.CppBinary;
import org.gradle.language.cpp.CppComponent;
import org.gradle.language.cpp.CppExecutable;
import org.gradle.language.cpp.CppLibrary;
import org.gradle.language.cpp.CppSharedLibrary;
import org.gradle.language.cpp.CppStaticLibrary;
import org.gradle.language.cpp.internal.DefaultCppBinary;
import org.gradle.language.cpp.tasks.CppCompile;
import org.gradle.language.nativeplatform.ComponentWithExecutable;
import org.gradle.nativeplatform.tasks.LinkExecutable;
import org.gradle.nativeplatform.tasks.LinkSharedLibrary;
import org.gradle.nativeplatform.test.cpp.CppTestExecutable;
import org.gradle.nativeplatform.test.cpp.CppTestSuite;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;
import org.gradle.nativeplatform.toolchain.internal.ToolType;
import org.gradle.nativeplatform.toolchain.internal.tools.CommandLineToolSearchResult;
import org.gradle.plugins.ide.internal.tooling.ToolingModelBuilderSupport;
import org.gradle.plugins.ide.internal.tooling.model.LaunchableGradleTask;
import org.gradle.tooling.internal.gradle.DefaultProjectIdentifier;
import org.gradle.tooling.internal.protocol.cpp.InternalCppApplication;
import org.gradle.tooling.internal.protocol.cpp.InternalCppExecutable;
import org.gradle.tooling.internal.protocol.cpp.InternalCppLibrary;
import org.gradle.tooling.internal.protocol.cpp.InternalCppSharedLibrary;
import org.gradle.tooling.internal.protocol.cpp.InternalCppStaticLibrary;
import org.gradle.tooling.internal.protocol.cpp.InternalCppTestSuite;
import org.gradle.tooling.model.cpp.CppProject;
import org.gradle.tooling.provider.model.ToolingModelBuilder;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class CppModelBuilder implements ToolingModelBuilder {
    @Override
    public boolean canBuild(String modelName) {
        return modelName.equals(CppProject.class.getName());
    }

    @Override
    public Object buildAll(String modelName, Project project) {
        DefaultProjectIdentifier projectIdentifier = new DefaultProjectIdentifier(project.getRootDir(), project.getPath());
        DefaultCppComponentModel mainComponent = null;
        CppApplication application = project.getComponents().withType(CppApplication.class).findByName("main");
        if (application != null) {
            mainComponent = new DefaultCppApplicationModel(application.getBaseName().get(), binariesFor(application, projectIdentifier));
        } else {
            CppLibrary library = project.getComponents().withType(CppLibrary.class).findByName("main");
            if (library != null) {
                mainComponent = new DefaultCppLibraryModel(library.getBaseName().get(), binariesFor(library, projectIdentifier));
            }
        }
        DefaultCppComponentModel testComponent = null;
        CppTestSuite testSuite = project.getComponents().withType(CppTestSuite.class).findByName("test");
        if (testSuite != null) {
            testComponent = new DefaultCppTestSuiteModel(testSuite.getBaseName().get(), binariesFor(testSuite, projectIdentifier));
        }
        return new DefaultCppProjectModel(projectIdentifier, mainComponent, testComponent);
    }

    private List<DefaultCppBinaryModel> binariesFor(CppComponent component, DefaultProjectIdentifier projectIdentifier) {
        ArrayList<DefaultCppBinaryModel> binaries = new ArrayList<DefaultCppBinaryModel>();
        for (CppBinary binary : component.getBinaries().get()) {
            DefaultCppBinary cppBinary = (DefaultCppBinary) binary;
            List<File> sourceFiles = new ArrayList<File>(binary.getCppSource().getFiles());
            CppCompile compileTask = binary.getCompileTask().get();
            List<File> systemIncludes = new ArrayList<File>(compileTask.getSystemIncludes().getFiles());
            List<File> userIncludes = new ArrayList<File>(compileTask.getIncludes().getFiles());
            List<DefaultMacroDirective> macroDefines = macroDefines(compileTask);
            List<String> additionalArgs = args(compileTask.getCompilerArgs().get());
            PlatformToolProvider platformToolProvider = cppBinary.getPlatformToolProvider();
            CommandLineToolSearchResult compilerLookup = platformToolProvider.locateTool(ToolType.CPP_COMPILER);
            File compilerExe = compilerLookup.isAvailable() ? compilerLookup.getTool() : null;
            LaunchableGradleTask compileTaskModel = ToolingModelBuilderSupport.buildFromTask(new LaunchableGradleTask(), projectIdentifier, compileTask);
            DefaultCompilationDetails compilationDetails = new DefaultCompilationDetails(compileTaskModel, compilerExe, compileTask.getObjectFileDir().get().getAsFile(), sourceFiles, systemIncludes, userIncludes, macroDefines, additionalArgs);
            if (binary instanceof CppExecutable || binary instanceof CppTestExecutable) {
                ComponentWithExecutable componentWithExecutable = (ComponentWithExecutable) binary;
                LinkExecutable linkTask = componentWithExecutable.getLinkTask().get();
                LaunchableGradleTask linkTaskModel = ToolingModelBuilderSupport.buildFromTask(new LaunchableGradleTask(), projectIdentifier, taskFor(componentWithExecutable.getExecutableFile()));
                DefaultLinkageDetails linkageDetails = new DefaultLinkageDetails(linkTaskModel, componentWithExecutable.getExecutableFile().get().getAsFile(), args(linkTask.getLinkerArgs().get()));
                binaries.add(new DefaultCppExecutableModel(binary.getName(), binary.getBaseName().get(), compilationDetails, linkageDetails));
            } else if (binary instanceof CppSharedLibrary) {
                CppSharedLibrary sharedLibrary = (CppSharedLibrary) binary;
                LinkSharedLibrary linkTask = sharedLibrary.getLinkTask().get();
                LaunchableGradleTask linkTaskModel = ToolingModelBuilderSupport.buildFromTask(new LaunchableGradleTask(), projectIdentifier, taskFor(sharedLibrary.getLinkFile()));
                DefaultLinkageDetails linkageDetails = new DefaultLinkageDetails(linkTaskModel, sharedLibrary.getLinkFile().get().getAsFile(), args(linkTask.getLinkerArgs().get()));
                binaries.add(new DefaultCppSharedLibraryModel(binary.getName(), binary.getBaseName().get(), compilationDetails, linkageDetails));
            } else if (binary instanceof CppStaticLibrary) {
                CppStaticLibrary staticLibrary = (CppStaticLibrary) binary;
                LaunchableGradleTask createTaskModel = ToolingModelBuilderSupport.buildFromTask(new LaunchableGradleTask(), projectIdentifier, taskFor(staticLibrary.getLinkFile()));
                DefaultLinkageDetails linkageDetails = new DefaultLinkageDetails(createTaskModel, staticLibrary.getLinkFile().get().getAsFile(), Collections.<String>emptyList());
                binaries.add(new DefaultCppStaticLibraryModel(binary.getName(), binary.getBaseName().get(), compilationDetails, linkageDetails));
            }
        }
        return binaries;
    }

    private Task taskFor(Provider<RegularFile> executableFile) {
        TaskDependencyContainer container = (TaskDependencyContainer) executableFile;
        // TODO - add something to C++ binary model instead of reverse engineering this
        TaskDependencyResolveContextImpl context = new TaskDependencyResolveContextImpl();
        container.visitDependencies(context);
        return context.task;
    }

    private List<String> args(List<String> compilerArgs) {
        return ImmutableList.copyOf(compilerArgs);
    }

    private List<DefaultMacroDirective> macroDefines(CppCompile compileTask) {
        if (compileTask.getMacros().isEmpty()) {
            return Collections.emptyList();
        }
        List<DefaultMacroDirective> macros = new ArrayList<DefaultMacroDirective>(compileTask.getMacros().size());
        for (Map.Entry<String, String> entry : compileTask.getMacros().entrySet()) {
            macros.add(new DefaultMacroDirective(entry.getKey(), entry.getValue()));
        }
        return macros;
    }

    public static class DefaultCppProjectModel implements Serializable {
        private final DefaultProjectIdentifier projectIdentifier;
        private final DefaultCppComponentModel mainComponent;
        private final DefaultCppComponentModel testComponent;

        public DefaultCppProjectModel(DefaultProjectIdentifier projectIdentifier, DefaultCppComponentModel mainComponent, DefaultCppComponentModel testComponent) {
            this.projectIdentifier = projectIdentifier;
            this.mainComponent = mainComponent;
            this.testComponent = testComponent;
        }

        public DefaultProjectIdentifier getProjectIdentifier() {
            return projectIdentifier;
        }

        public DefaultCppComponentModel getMainComponent() {
            return mainComponent;
        }

        public DefaultCppComponentModel getTestComponent() {
            return testComponent;
        }
    }

    public static class DefaultMacroDirective implements Serializable {
        private final String name;
        private final String value;

        public DefaultMacroDirective(String name, String value) {
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public String getValue() {
            return value;
        }
    }

    public static class DefaultCompilationDetails implements Serializable {
        private final LaunchableGradleTask compileTask;
        private final File compilerExe;
        private final File workingDir;
        private final List<File> sources;
        private final List<File> systemHeaderDirs;
        private final List<File> userHeaderDirs;
        private final List<DefaultMacroDirective> macroDefines;
        private final List<String> additionalArgs;

        public DefaultCompilationDetails(LaunchableGradleTask compileTask, File compilerExe, File workingDir, List<File> sources, List<File> systemHeaderDirs, List<File> userHeaderDirs, List<DefaultMacroDirective> macroDefines, List<String> additionalArgs) {
            this.compileTask = compileTask;
            this.compilerExe = compilerExe;
            this.workingDir = workingDir;
            this.sources = sources;
            this.systemHeaderDirs = systemHeaderDirs;
            this.userHeaderDirs = userHeaderDirs;
            this.macroDefines = macroDefines;
            this.additionalArgs = additionalArgs;
        }

        public LaunchableGradleTask getCompileTask() {
            return compileTask;
        }

        public File getCompilerExecutable() {
            return compilerExe;
        }

        public File getCompileWorkingDir() {
            return workingDir;
        }

        public List<File> getSources() {
            return sources;
        }

        public List<File> getFrameworkSearchPaths() {
            return Collections.emptyList();
        }

        public List<File> getSystemHeaderSearchPaths() {
            return systemHeaderDirs;
        }

        public List<File> getUserHeaderSearchPaths() {
            return userHeaderDirs;
        }

        public List<DefaultMacroDirective> getMacroDefines() {
            return macroDefines;
        }

        public List<String> getMacroUndefines() {
            return Collections.emptyList();
        }

        public List<String> getAdditionalArgs() {
            return additionalArgs;
        }
    }

    public static class DefaultLinkageDetails implements Serializable {
        private final LaunchableGradleTask linkTask;
        private final File outputLocation;
        private final List<String> additionalArgs;

        public DefaultLinkageDetails(LaunchableGradleTask linkTask, File outputLocation, List<String> additionalArgs) {
            this.linkTask = linkTask;
            this.outputLocation = outputLocation;
            this.additionalArgs = additionalArgs;
        }

        public LaunchableGradleTask getLinkTask() {
            return linkTask;
        }

        public File getOutputLocation() {
            return outputLocation;
        }

        public List<String> getAdditionalArgs() {
            return additionalArgs;
        }
    }

    public static class DefaultCppBinaryModel implements Serializable {
        private final String name;
        private final String baseName;
        private final DefaultCompilationDetails compilationDetails;
        private final DefaultLinkageDetails linkageDetails;

        public DefaultCppBinaryModel(String name, String baseName, DefaultCompilationDetails compilationDetails, DefaultLinkageDetails linkageDetails) {
            this.name = name;
            this.baseName = baseName;
            this.compilationDetails = compilationDetails;
            this.linkageDetails = linkageDetails;
        }

        public String getName() {
            return name;
        }

        public String getBaseName() {
            return baseName;
        }

        public DefaultCompilationDetails getCompilationDetails() {
            return compilationDetails;
        }

        public DefaultLinkageDetails getLinkageDetails() {
            return linkageDetails;
        }
    }

    public static class DefaultCppExecutableModel extends DefaultCppBinaryModel implements InternalCppExecutable {
        public DefaultCppExecutableModel(String name, String baseName, DefaultCompilationDetails compilationDetails, DefaultLinkageDetails linkageDetails) {
            super(name, baseName, compilationDetails, linkageDetails);
        }
    }

    public static class DefaultCppSharedLibraryModel extends DefaultCppBinaryModel implements InternalCppSharedLibrary {
        public DefaultCppSharedLibraryModel(String name, String baseName, DefaultCompilationDetails compilationDetails, DefaultLinkageDetails linkageDetails) {
            super(name, baseName, compilationDetails, linkageDetails);
        }
    }

    public static class DefaultCppStaticLibraryModel extends DefaultCppBinaryModel implements InternalCppStaticLibrary {
        public DefaultCppStaticLibraryModel(String name, String baseName, DefaultCompilationDetails compilationDetails, DefaultLinkageDetails linkageDetails) {
            super(name, baseName, compilationDetails, linkageDetails);
        }
    }

    public static class DefaultCppComponentModel implements Serializable {
        private final String baseName;
        private final List<DefaultCppBinaryModel> binaries;

        public DefaultCppComponentModel(String baseName, List<DefaultCppBinaryModel> binaries) {
            this.baseName = baseName;
            this.binaries = binaries;
        }

        public String getBaseName() {
            return baseName;
        }

        public List<DefaultCppBinaryModel> getBinaries() {
            return binaries;
        }
    }

    public static class DefaultCppApplicationModel extends DefaultCppComponentModel implements InternalCppApplication {
        public DefaultCppApplicationModel(String baseName, List<DefaultCppBinaryModel> binaries) {
            super(baseName, binaries);
        }
    }

    public static class DefaultCppLibraryModel extends DefaultCppComponentModel implements InternalCppLibrary {
        public DefaultCppLibraryModel(String baseName, List<DefaultCppBinaryModel> binaries) {
            super(baseName, binaries);
        }
    }

    public static class DefaultCppTestSuiteModel extends DefaultCppComponentModel implements InternalCppTestSuite {
        public DefaultCppTestSuiteModel(String baseName, List<DefaultCppBinaryModel> binaries) {
            super(baseName, binaries);
        }
    }

    private static class TaskDependencyResolveContextImpl implements TaskDependencyResolveContext {
        private Task task;

        @Override
        public void add(Object dependency) {
            if (dependency instanceof TaskDependencyContainer) {
                TaskDependencyContainer container = (TaskDependencyContainer) dependency;
                container.visitDependencies(this);
            } else {
                task = (Task) dependency;
            }
        }

        @Override
        public Task getTask() {
            return null;
        }
    }
}
