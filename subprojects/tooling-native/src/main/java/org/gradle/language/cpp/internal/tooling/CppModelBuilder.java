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
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.language.cpp.CppApplication;
import org.gradle.language.cpp.CppBinary;
import org.gradle.language.cpp.CppComponent;
import org.gradle.language.cpp.CppExecutable;
import org.gradle.language.cpp.CppLibrary;
import org.gradle.language.cpp.CppSharedLibrary;
import org.gradle.language.cpp.CppStaticLibrary;
import org.gradle.language.cpp.internal.DefaultCppBinary;
import org.gradle.language.cpp.internal.DefaultCppLibrary;
import org.gradle.language.cpp.tasks.CppCompile;
import org.gradle.language.nativeplatform.ComponentWithExecutable;
import org.gradle.nativeplatform.internal.CompilerOutputFileNamingScheme;
import org.gradle.nativeplatform.internal.CompilerOutputFileNamingSchemeFactory;
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
import org.gradle.tooling.model.cpp.CppProject;
import org.gradle.tooling.provider.model.ToolingModelBuilder;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CppModelBuilder implements ToolingModelBuilder {
    @Override
    public boolean canBuild(String modelName) {
        return modelName.equals(CppProject.class.getName());
    }

    @Override
    public Object buildAll(String modelName, Project project) {
        DefaultProjectIdentifier projectIdentifier = new DefaultProjectIdentifier(project.getRootDir(), project.getPath());
        CompilerOutputFileNamingSchemeFactory namingSchemeFactory = new CompilerOutputFileNamingSchemeFactory(((ProjectInternal) project).getFileResolver());
        DefaultCppComponentModel mainComponent = null;
        CppApplication application = project.getComponents().withType(CppApplication.class).findByName("main");
        if (application != null) {
            mainComponent = new DefaultCppApplicationModel(application.getName(), application.getBaseName().get(), binariesFor(application, application.getPrivateHeaderDirs(), projectIdentifier, namingSchemeFactory));
        } else {
            DefaultCppLibrary library = (DefaultCppLibrary) project.getComponents().withType(CppLibrary.class).findByName("main");
            if (library != null) {
                mainComponent = new DefaultCppLibraryModel(library.getName(), library.getBaseName().get(), binariesFor(library, library.getAllHeaderDirs(), projectIdentifier, namingSchemeFactory));
            }
        }
        DefaultCppComponentModel testComponent = null;
        CppTestSuite testSuite = project.getComponents().withType(CppTestSuite.class).findByName("test");
        if (testSuite != null) {
            testComponent = new DefaultCppTestSuiteModel(testSuite.getName(), testSuite.getBaseName().get(), binariesFor(testSuite, testSuite.getPrivateHeaderDirs(), projectIdentifier, namingSchemeFactory));
        }
        return new DefaultCppProjectModel(projectIdentifier, mainComponent, testComponent);
    }

    private List<DefaultCppBinaryModel> binariesFor(CppComponent component, Iterable<File> headerDirs, DefaultProjectIdentifier projectIdentifier, CompilerOutputFileNamingSchemeFactory namingSchemeFactory) {
        List<File> headerDirsCopy = ImmutableList.copyOf(headerDirs);
        List<DefaultCppBinaryModel> binaries = new ArrayList<DefaultCppBinaryModel>();
        for (CppBinary binary : component.getBinaries().get()) {
            DefaultCppBinary cppBinary = (DefaultCppBinary) binary;
            PlatformToolProvider platformToolProvider = cppBinary.getPlatformToolProvider();
            CppCompile compileTask = binary.getCompileTask().get();
            List<DefaultSourceFile> sourceFiles = sourceFiles(namingSchemeFactory, platformToolProvider, compileTask.getObjectFileDir().get().getAsFile(), binary.getCppSource().getFiles());
            List<File> systemIncludes = ImmutableList.copyOf(compileTask.getSystemIncludes().getFiles());
            List<File> userIncludes = ImmutableList.copyOf(compileTask.getIncludes().getFiles());
            List<DefaultMacroDirective> macroDefines = macroDefines(compileTask);
            List<String> additionalArgs = args(compileTask.getCompilerArgs().get());
            CommandLineToolSearchResult compilerLookup = platformToolProvider.locateTool(ToolType.CPP_COMPILER);
            File compilerExe = compilerLookup.isAvailable() ? compilerLookup.getTool() : null;
            LaunchableGradleTask compileTaskModel = buildLaunchableTask(projectIdentifier, compileTask);
            DefaultCompilationDetails compilationDetails = new DefaultCompilationDetails(compileTaskModel, compilerExe, compileTask.getObjectFileDir().get().getAsFile(), sourceFiles, headerDirsCopy,  systemIncludes, userIncludes, macroDefines, additionalArgs);
            if (binary instanceof CppExecutable || binary instanceof CppTestExecutable) {
                ComponentWithExecutable componentWithExecutable = (ComponentWithExecutable) binary;
                LinkExecutable linkTask = componentWithExecutable.getLinkTask().get();
                LaunchableGradleTask linkTaskModel = buildLaunchableTask(projectIdentifier, componentWithExecutable.getExecutableFileProducer().get());
                DefaultLinkageDetails linkageDetails = new DefaultLinkageDetails(linkTaskModel, componentWithExecutable.getExecutableFile().get().getAsFile(), args(linkTask.getLinkerArgs().get()));
                binaries.add(new DefaultCppExecutableModel(binary.getName(), cppBinary.getIdentity().getName(), binary.getBaseName().get(), compilationDetails, linkageDetails));
            } else if (binary instanceof CppSharedLibrary) {
                CppSharedLibrary sharedLibrary = (CppSharedLibrary) binary;
                LinkSharedLibrary linkTask = sharedLibrary.getLinkTask().get();
                LaunchableGradleTask linkTaskModel = buildLaunchableTask(projectIdentifier, sharedLibrary.getLinkFileProducer().get());
                DefaultLinkageDetails linkageDetails = new DefaultLinkageDetails(linkTaskModel, sharedLibrary.getLinkFile().get().getAsFile(), args(linkTask.getLinkerArgs().get()));
                binaries.add(new DefaultCppSharedLibraryModel(binary.getName(), cppBinary.getIdentity().getName(), binary.getBaseName().get(), compilationDetails, linkageDetails));
            } else if (binary instanceof CppStaticLibrary) {
                CppStaticLibrary staticLibrary = (CppStaticLibrary) binary;
                LaunchableGradleTask createTaskModel = buildLaunchableTask(projectIdentifier, staticLibrary.getLinkFileProducer().get());
                DefaultLinkageDetails linkageDetails = new DefaultLinkageDetails(createTaskModel, staticLibrary.getLinkFile().get().getAsFile(), Collections.<String>emptyList());
                binaries.add(new DefaultCppStaticLibraryModel(binary.getName(), cppBinary.getIdentity().getName(), binary.getBaseName().get(), compilationDetails, linkageDetails));
            }
        }
        return binaries;
    }

    @Nonnull
    private static LaunchableGradleTask buildLaunchableTask(DefaultProjectIdentifier projectIdentifier, Task task) {
        LaunchableGradleTask launchableGradleTask = ToolingModelBuilderSupport.buildFromTask(new LaunchableGradleTask(), projectIdentifier, task);
        launchableGradleTask.setBuildTreePath(((TaskInternal) task).getIdentityPath().getPath());
        return launchableGradleTask;
    }

    private List<DefaultSourceFile> sourceFiles(CompilerOutputFileNamingSchemeFactory namingSchemeFactory, PlatformToolProvider platformToolProvider, File objDir, Set<File> files) {
        CompilerOutputFileNamingScheme namingScheme = namingSchemeFactory.create().withObjectFileNameSuffix(platformToolProvider.getObjectFileExtension()).withOutputBaseFolder(objDir);
        List<DefaultSourceFile> result = new ArrayList<DefaultSourceFile>(files.size());
        for (File file : files) {
            result.add(new DefaultSourceFile(file, namingScheme.map(file)));
        }
        return result;
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
}
