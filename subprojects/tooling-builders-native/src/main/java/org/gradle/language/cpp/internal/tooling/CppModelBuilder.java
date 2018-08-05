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
import org.gradle.language.cpp.tasks.CppCompile;
import org.gradle.language.nativeplatform.ComponentWithExecutable;
import org.gradle.nativeplatform.tasks.LinkExecutable;
import org.gradle.nativeplatform.tasks.LinkSharedLibrary;
import org.gradle.nativeplatform.test.cpp.CppTestExecutable;
import org.gradle.nativeplatform.test.cpp.CppTestSuite;
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
        DefaultCppComponent mainComponent = null;
        CppApplication application = project.getComponents().withType(CppApplication.class).findByName("main");
        if (application != null) {
            mainComponent = new DefaultCppApplication(application.getBaseName().get(), binariesFor(application, projectIdentifier));
        } else {
            CppLibrary library = project.getComponents().withType(CppLibrary.class).findByName("main");
            if (library != null) {
                mainComponent = new DefaultCppLibrary(library.getBaseName().get(), binariesFor(library, projectIdentifier));
            }
        }
        DefaultCppComponent testComponent = null;
        CppTestSuite testSuite = project.getComponents().withType(CppTestSuite.class).findByName("test");
        if (testSuite != null) {
            testComponent = new DefaultCppTestSuite(testSuite.getBaseName().get(), binariesFor(testSuite, projectIdentifier));
        }
        return new DefaultCppProject(projectIdentifier, mainComponent, testComponent);
    }

    private List<DefaultCppBinary> binariesFor(CppComponent component, DefaultProjectIdentifier projectIdentifier) {
        ArrayList<DefaultCppBinary> binaries = new ArrayList<DefaultCppBinary>();
        for (CppBinary binary : component.getBinaries().get()) {
            List<File> sourceFiles = new ArrayList<File>(binary.getCppSource().getFiles());
            CppCompile compileTask = binary.getCompileTask().get();
            List<File> systemIncludes = new ArrayList<File>(compileTask.getSystemIncludes().getFiles());
            List<File> userIncludes = new ArrayList<File>(compileTask.getIncludes().getFiles());
            List<DefaultMacroDirective> macroDefines = macroDefines(compileTask);
            List<String> additionalArgs = args(compileTask.getCompilerArgs().get());
            LaunchableGradleTask compileTaskModel = ToolingModelBuilderSupport.buildFromTask(new LaunchableGradleTask(), projectIdentifier, compileTask);
            DefaultCompilationDetails compilationDetails = new DefaultCompilationDetails(compileTaskModel, sourceFiles, systemIncludes, userIncludes, macroDefines, additionalArgs);
            if (binary instanceof CppExecutable || binary instanceof CppTestExecutable) {
                ComponentWithExecutable componentWithExecutable = (ComponentWithExecutable) binary;
                LinkExecutable linkTask = componentWithExecutable.getLinkTask().get();
                LaunchableGradleTask linkTaskModel = ToolingModelBuilderSupport.buildFromTask(new LaunchableGradleTask(), projectIdentifier, taskFor(componentWithExecutable.getExecutableFile()));
                DefaultLinkageDetails linkageDetails = new DefaultLinkageDetails(linkTaskModel, componentWithExecutable.getExecutableFile().get().getAsFile(), args(linkTask.getLinkerArgs().get()));
                binaries.add(new DefaultCppExecutable(binary.getName(), binary.getBaseName().get(), compilationDetails, linkageDetails));
            } else if (binary instanceof CppSharedLibrary) {
                CppSharedLibrary sharedLibrary = (CppSharedLibrary) binary;
                LinkSharedLibrary linkTask = sharedLibrary.getLinkTask().get();
                LaunchableGradleTask linkTaskModel = ToolingModelBuilderSupport.buildFromTask(new LaunchableGradleTask(), projectIdentifier, taskFor(sharedLibrary.getLinkFile()));
                DefaultLinkageDetails linkageDetails = new DefaultLinkageDetails(linkTaskModel, sharedLibrary.getLinkFile().get().getAsFile(), args(linkTask.getLinkerArgs().get()));
                binaries.add(new DefaultCppSharedLibrary(binary.getName(), binary.getBaseName().get(), compilationDetails, linkageDetails));
            } else if (binary instanceof CppStaticLibrary) {
                CppStaticLibrary staticLibrary = (CppStaticLibrary) binary;
                LaunchableGradleTask createTaskModel = ToolingModelBuilderSupport.buildFromTask(new LaunchableGradleTask(), projectIdentifier, taskFor(staticLibrary.getLinkFile()));
                DefaultLinkageDetails linkageDetails = new DefaultLinkageDetails(createTaskModel, staticLibrary.getLinkFile().get().getAsFile(), Collections.<String>emptyList());
                binaries.add(new DefaultCppStaticLibrary(binary.getName(), binary.getBaseName().get(), compilationDetails, linkageDetails));
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

    public static class DefaultCppProject implements Serializable {
        private final DefaultProjectIdentifier projectIdentifier;
        private final DefaultCppComponent mainComponent;
        private final DefaultCppComponent testComponent;

        public DefaultCppProject(DefaultProjectIdentifier projectIdentifier, DefaultCppComponent mainComponent, DefaultCppComponent testComponent) {
            this.projectIdentifier = projectIdentifier;
            this.mainComponent = mainComponent;
            this.testComponent = testComponent;
        }

        public DefaultProjectIdentifier getProjectIdentifier() {
            return projectIdentifier;
        }

        public DefaultCppComponent getMainComponent() {
            return mainComponent;
        }

        public DefaultCppComponent getTestComponent() {
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
        private final List<File> sources;
        private final List<File> systemHeaderDirs;
        private final List<File> userHeaderDirs;
        private final List<DefaultMacroDirective> macroDefines;
        private final List<String> additionalArgs;

        public DefaultCompilationDetails(LaunchableGradleTask compileTask, List<File> sources, List<File> systemHeaderDirs, List<File> userHeaderDirs, List<DefaultMacroDirective> macroDefines, List<String> additionalArgs) {
            this.compileTask = compileTask;
            this.sources = sources;
            this.systemHeaderDirs = systemHeaderDirs;
            this.userHeaderDirs = userHeaderDirs;
            this.macroDefines = macroDefines;
            this.additionalArgs = additionalArgs;
        }

        public LaunchableGradleTask getCompileTask() {
            return compileTask;
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

    public static class DefaultCppBinary implements Serializable {
        private final String name;
        private final String baseName;
        private final DefaultCompilationDetails compilationDetails;
        private final DefaultLinkageDetails linkageDetails;

        public DefaultCppBinary(String name, String baseName, DefaultCompilationDetails compilationDetails, DefaultLinkageDetails linkageDetails) {
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

    public static class DefaultCppExecutable extends DefaultCppBinary implements InternalCppExecutable {
        public DefaultCppExecutable(String name, String baseName, DefaultCompilationDetails compilationDetails, DefaultLinkageDetails linkageDetails) {
            super(name, baseName, compilationDetails, linkageDetails);
        }
    }

    public static class DefaultCppSharedLibrary extends DefaultCppBinary implements InternalCppSharedLibrary {
        public DefaultCppSharedLibrary(String name, String baseName, DefaultCompilationDetails compilationDetails, DefaultLinkageDetails linkageDetails) {
            super(name, baseName, compilationDetails, linkageDetails);
        }
    }

    public static class DefaultCppStaticLibrary extends DefaultCppBinary implements InternalCppStaticLibrary {
        public DefaultCppStaticLibrary(String name, String baseName, DefaultCompilationDetails compilationDetails, DefaultLinkageDetails linkageDetails) {
            super(name, baseName, compilationDetails, linkageDetails);
        }
    }

    public static class DefaultCppComponent implements Serializable {
        private final String baseName;
        private final List<DefaultCppBinary> binaries;

        public DefaultCppComponent(String baseName, List<DefaultCppBinary> binaries) {
            this.baseName = baseName;
            this.binaries = binaries;
        }

        public String getBaseName() {
            return baseName;
        }

        public List<DefaultCppBinary> getBinaries() {
            return binaries;
        }
    }

    public static class DefaultCppApplication extends DefaultCppComponent implements InternalCppApplication {
        public DefaultCppApplication(String baseName, List<DefaultCppBinary> binaries) {
            super(baseName, binaries);
        }
    }

    public static class DefaultCppLibrary extends DefaultCppComponent implements InternalCppLibrary {
        public DefaultCppLibrary(String baseName, List<DefaultCppBinary> binaries) {
            super(baseName, binaries);
        }
    }

    public static class DefaultCppTestSuite extends DefaultCppComponent implements InternalCppTestSuite {
        public DefaultCppTestSuite(String baseName, List<DefaultCppBinary> binaries) {
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
