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

import org.gradle.api.Project;
import org.gradle.language.cpp.CppApplication;
import org.gradle.language.cpp.CppBinary;
import org.gradle.language.cpp.CppComponent;
import org.gradle.language.cpp.CppExecutable;
import org.gradle.language.cpp.CppLibrary;
import org.gradle.language.cpp.CppSharedLibrary;
import org.gradle.language.cpp.CppStaticLibrary;
import org.gradle.language.cpp.tasks.CppCompile;
import org.gradle.nativeplatform.test.cpp.CppTestExecutable;
import org.gradle.nativeplatform.test.cpp.CppTestSuite;
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

public class CppModelBuilder implements ToolingModelBuilder {
    @Override
    public boolean canBuild(String modelName) {
        return modelName.equals(CppProject.class.getName());
    }

    @Override
    public Object buildAll(String modelName, Project project) {
        DefaultCppComponent mainComponent = null;
        CppApplication application = project.getComponents().withType(CppApplication.class).findByName("main");
        if (application != null) {
            mainComponent = new DefaultCppApplication(application.getBaseName().get(), binariesFor(application));
        } else {
            CppLibrary library = project.getComponents().withType(CppLibrary.class).findByName("main");
            if (library != null) {
                mainComponent = new DefaultCppLibrary(library.getBaseName().get(), binariesFor(library));
            }
        }
        DefaultCppComponent testComponent = null;
        CppTestSuite testSuite = project.getComponents().withType(CppTestSuite.class).findByName("test");
        if (testSuite != null) {
            testComponent = new DefaultCppTestSuite(testSuite.getBaseName().get(), binariesFor(testSuite));
        }
        DefaultProjectIdentifier projectIdentifier = new DefaultProjectIdentifier(project.getRootDir(), project.getPath());
        return new DefaultCppProject(projectIdentifier, mainComponent, testComponent);
    }

    private List<DefaultCppBinary> binariesFor(CppComponent component) {
        ArrayList<DefaultCppBinary> binaries = new ArrayList<DefaultCppBinary>();
        for (CppBinary binary : component.getBinaries().get()) {
            List<File> sourceFiles = new ArrayList<File>(binary.getCppSource().getFiles());
            CppCompile compileTask = binary.getCompileTask().get();
            List<File> systemIncludes = new ArrayList<File>(compileTask.getSystemIncludes().getFiles());
            List<File> userIncludes = new ArrayList<File>(compileTask.getIncludes().getFiles());
            DefaultCompilationDetails compilationDetails = new DefaultCompilationDetails(sourceFiles, systemIncludes, userIncludes);
            if (binary instanceof CppExecutable || binary instanceof CppTestExecutable) {
                binaries.add(new DefaultCppExecutable(binary.getName(), binary.getBaseName().get(), compilationDetails));
            } else if (binary instanceof CppSharedLibrary) {
                binaries.add(new DefaultCppSharedLibrary(binary.getName(), binary.getBaseName().get(), compilationDetails));
            } else if (binary instanceof CppStaticLibrary) {
                binaries.add(new DefaultCppStaticLibrary(binary.getName(), binary.getBaseName().get(), compilationDetails));
            }
        }
        return binaries;
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

    public static class DefaultCompilationDetails implements Serializable {
        private final List<File> sources;
        private final List<File> systemHeaderDirs;
        private final List<File> userHeaderDirs;

        public DefaultCompilationDetails(List<File> sources, List<File> systemHeaderDirs, List<File> userHeaderDirs) {
            this.sources = sources;
            this.systemHeaderDirs = systemHeaderDirs;
            this.userHeaderDirs = userHeaderDirs;
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
    }

    public static class DefaultCppBinary implements Serializable {
        private final String name;
        private final String baseName;
        private final DefaultCompilationDetails compilationDetails;

        public DefaultCppBinary(String name, String baseName, DefaultCompilationDetails compilationDetails) {
            this.name = name;
            this.baseName = baseName;
            this.compilationDetails = compilationDetails;
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
    }

    public static class DefaultCppExecutable extends DefaultCppBinary implements InternalCppExecutable {
        public DefaultCppExecutable(String name, String baseName, DefaultCompilationDetails compilationDetails) {
            super(name, baseName, compilationDetails);
        }
    }

    public static class DefaultCppSharedLibrary extends DefaultCppBinary implements InternalCppSharedLibrary {
        public DefaultCppSharedLibrary(String name, String baseName, DefaultCompilationDetails compilationDetails) {
            super(name, baseName, compilationDetails);
        }
    }

    public static class DefaultCppStaticLibrary extends DefaultCppBinary implements InternalCppStaticLibrary {
        public DefaultCppStaticLibrary(String name, String baseName, DefaultCompilationDetails compilationDetails) {
            super(name, baseName, compilationDetails);
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
}
