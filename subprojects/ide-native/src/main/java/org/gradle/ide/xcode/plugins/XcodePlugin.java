/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.ide.xcode.plugins;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Incubating;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.LocalComponentRegistry;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectLocalComponentProvider;
import org.gradle.api.internal.artifacts.publish.DefaultPublishArtifact;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.Delete;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.ide.xcode.XcodeExtension;
import org.gradle.ide.xcode.XcodeProject;
import org.gradle.ide.xcode.internal.DefaultXcodeExtension;
import org.gradle.ide.xcode.internal.DefaultXcodeProject;
import org.gradle.ide.xcode.internal.XcodeTarget;
import org.gradle.ide.xcode.internal.xcodeproj.FileTypes;
import org.gradle.ide.xcode.internal.xcodeproj.GidGenerator;
import org.gradle.ide.xcode.internal.xcodeproj.PBXTarget;
import org.gradle.ide.xcode.tasks.GenerateSchemeFileTask;
import org.gradle.ide.xcode.tasks.GenerateWorkspaceSettingsFileTask;
import org.gradle.ide.xcode.tasks.GenerateXcodeProjectFileTask;
import org.gradle.ide.xcode.tasks.GenerateXcodeWorkspaceFileTask;
import org.gradle.initialization.ProjectPathRegistry;
import org.gradle.internal.component.local.model.LocalComponentArtifactMetadata;
import org.gradle.internal.component.local.model.PublishArtifactLocalArtifactMetadata;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.language.cpp.CppComponent;
import org.gradle.language.cpp.plugins.CppExecutablePlugin;
import org.gradle.language.cpp.plugins.CppLibraryPlugin;
import org.gradle.language.swift.SwiftComponent;
import org.gradle.language.swift.plugins.SwiftExecutablePlugin;
import org.gradle.language.swift.plugins.SwiftLibraryPlugin;
import org.gradle.nativeplatform.tasks.AbstractLinkTask;
import org.gradle.nativeplatform.test.xctest.SwiftXCTestSuite;
import org.gradle.nativeplatform.test.xctest.plugins.XCTestConventionPlugin;
import org.gradle.plugins.ide.internal.IdePlugin;
import org.gradle.util.CollectionUtils;
import org.gradle.util.Path;

import javax.inject.Inject;
import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;

import static org.gradle.internal.component.local.model.DefaultProjectComponentIdentifier.newProjectId;

/**
 * A plugin for creating a XCode project for a gradle project.
 *
 * @since 4.2
 */
@Incubating
public class XcodePlugin extends IdePlugin {
    private final GidGenerator gidGenerator;
    private final ObjectFactory objectFactory;
    private DefaultXcodeExtension xcode;

    @Inject
    public XcodePlugin(GidGenerator gidGenerator, ObjectFactory objectFactory) {
        this.gidGenerator = gidGenerator;
        this.objectFactory = objectFactory;
    }

    @Override
    protected String getLifecycleTaskName() {
        return "xcode";
    }

    @Override
    protected void onApply(final Project project) {
        Task lifecycleTask = getLifecycleTask();
        lifecycleTask.setDescription("Generates XCode project files (pbxproj, xcworkspace, xcscheme)");

        xcode = (DefaultXcodeExtension) project.getExtensions().create(XcodeExtension.class, "xcode", DefaultXcodeExtension.class, objectFactory);
        xcode.getProject().setLocationDir(project.file(project.getName() + ".xcodeproj"));

        if (isRoot(project)) {
            GenerateXcodeWorkspaceFileTask workspaceTask = createWorkspaceTask(project);
            addIncludedBuildToWorkspace(project, workspaceTask);
            lifecycleTask.dependsOn(workspaceTask);
        }

        GenerateXcodeProjectFileTask projectTask = createProjectTask(project);
        lifecycleTask.dependsOn(projectTask);
        projectTask.dependsOn(project.getTasks().withType(GenerateSchemeFileTask.class));

        project.getTasks().addRule("Xcode bridge tasks begin with _xcode. Do not call these directly.", new XcodeBridge(xcode.getProject(), project));

        configureForSwiftPlugin(project);
        configureForCppPlugin(project);

        includeBuildFilesInProject(project);
        configureXcodeCleanTask(project);
        registerXcodeProjectArtifact(project, projectTask);
    }

    private void includeBuildFilesInProject(Project project) {
        // TODO: Add other build like files `build.gradle.kts`, `settings.gradle(.kts)`, other `.gradle`, `gradle.properties`
        if (project.getBuildFile().exists()) {
            xcode.getProject().getGroups().getRoot().from(project.getBuildFile());
        }
    }

    private void configureXcodeCleanTask(Project project) {
        getCleanTask().setDescription("Cleans XCode project files (xcodeproj)");
        Delete cleanTask = project.getTasks().create("cleanXcodeProject", Delete.class);
        cleanTask.delete(xcode.getProject().getLocationDir());
        if (isRoot(project)) {
            cleanTask.delete(toXcodeWorkspacePackageDir(project));
        }
        getCleanTask().dependsOn(cleanTask);
    }

    private GenerateXcodeProjectFileTask createProjectTask(final Project project) {
        File xcodeProjectPackageDir = xcode.getProject().getLocationDir();

        GenerateWorkspaceSettingsFileTask workspaceSettingsFileTask = project.getTasks().create("xcodeProjectWorkspaceSettings", GenerateWorkspaceSettingsFileTask.class);
        workspaceSettingsFileTask.setOutputFile(new File(xcodeProjectPackageDir, "project.xcworkspace/xcshareddata/WorkspaceSettings.xcsettings"));

        GenerateXcodeProjectFileTask projectFileTask = project.getTasks().create("xcodeProject", GenerateXcodeProjectFileTask.class);
        projectFileTask.dependsOn(workspaceSettingsFileTask);
        projectFileTask.setXcodeProject(xcode.getProject());
        projectFileTask.setOutputFile(new File(xcodeProjectPackageDir, "project.pbxproj"));

        return projectFileTask;
    }

    private GenerateXcodeWorkspaceFileTask createWorkspaceTask(Project project) {
        File xcodeWorkspacePackageDir = toXcodeWorkspacePackageDir(project);

        GenerateWorkspaceSettingsFileTask workspaceSettingsFileTask = project.getTasks().create("xcodeWorkspaceWorkspaceSettings", GenerateWorkspaceSettingsFileTask.class);
        workspaceSettingsFileTask.setOutputFile(new File(xcodeWorkspacePackageDir, "xcshareddata/WorkspaceSettings.xcsettings"));

        GenerateXcodeWorkspaceFileTask workspaceFileTask = project.getTasks().create("xcodeWorkspace", GenerateXcodeWorkspaceFileTask.class);
        workspaceFileTask.dependsOn(workspaceSettingsFileTask);
        workspaceFileTask.setOutputFile(new File(xcodeWorkspacePackageDir, "contents.xcworkspacedata"));

        return workspaceFileTask;
    }

    private String getBridgeTaskPath(Project project) {
        String projectPath = "";
        if (!isRoot(project)) {
            projectPath = project.getPath();
        }
        return projectPath + ":_xcode__${ACTION}_${PRODUCT_NAME}_${CONFIGURATION}";
    }

    private void configureForSwiftPlugin(final Project project) {
        project.getPlugins().withType(SwiftExecutablePlugin.class, new Action<SwiftExecutablePlugin>() {
            @Override
            public void execute(SwiftExecutablePlugin plugin) {
                configureXcodeForSwift(project, PBXTarget.ProductType.TOOL);
            }
        });

        project.getPlugins().withType(SwiftLibraryPlugin.class, new Action<SwiftLibraryPlugin>() {
            @Override
            public void execute(SwiftLibraryPlugin plugin) {
                configureXcodeForSwift(project, PBXTarget.ProductType.DYNAMIC_LIBRARY);
            }
        });

        project.getPlugins().withType(XCTestConventionPlugin.class, new Action<XCTestConventionPlugin>() {
            @Override
            public void execute(XCTestConventionPlugin plugin) {
                configureXcodeForXCTest(project, PBXTarget.ProductType.UNIT_TEST);
            }
        });
    }

    private void configureXcodeForXCTest(final Project project, PBXTarget.ProductType productType) {
        SwiftXCTestSuite component = project.getExtensions().getByType(SwiftXCTestSuite.class);
        FileCollection sources = component.getSwiftSource();
        xcode.getProject().getGroups().getTests().from(sources);

        String targetName = component.getModule().get() + " " + toString(productType);
        XcodeTarget target = newTarget(targetName, component.getModule().get(), productType, toGradleCommand(project.getRootProject()), getBridgeTaskPath(project), component.getDevelopmentBinary().getInstallDirectory(), component.getDevelopmentBinary().getInstallDirectory(), sources);
        target.getCompileModules().from(component.getDevelopmentBinary().getCompileModules());
        xcode.getProject().addTarget(target);
    }

    private void configureXcodeForSwift(final Project project, PBXTarget.ProductType productType) {
        // TODO: Assumes there's a single 'main' Swift component
        SwiftComponent component = project.getComponents().withType(SwiftComponent.class).getByName("main");
        FileCollection sources = component.getSwiftSource();
        xcode.getProject().getGroups().getSources().from(sources);

        // TODO - Reuse the logic from `swift-executable` or `swift-library` to determine the link task path
        // TODO - should use the _install_ task for an executable
        AbstractLinkTask linkDebug = (AbstractLinkTask) project.getTasks().getByName("linkDebug");
        AbstractLinkTask linkRelease = (AbstractLinkTask) project.getTasks().getByName("linkRelease");

        String targetName = component.getModule().get() + " " + toString(productType);
        XcodeTarget target = newTarget(targetName, component.getModule().get(), productType, toGradleCommand(project.getRootProject()), getBridgeTaskPath(project), linkDebug.getBinaryFile(), linkRelease.getBinaryFile(), sources);
        target.getCompileModules().from(component.getDevelopmentBinary().getCompileModules());
        xcode.getProject().addTarget(target);

        createSchemeTask(project.getTasks(), targetName, xcode.getProject());
    }

    private void configureForCppPlugin(final Project project) {
        project.getPlugins().withType(CppExecutablePlugin.class, new Action<CppExecutablePlugin>() {
            @Override
            public void execute(CppExecutablePlugin plugin) {
                configureXcodeForCpp(project, PBXTarget.ProductType.TOOL);
            }
        });

        project.getPlugins().withType(CppLibraryPlugin.class, new Action<CppLibraryPlugin>() {
            @Override
            public void execute(CppLibraryPlugin plugin) {
                configureXcodeForCpp(project, PBXTarget.ProductType.DYNAMIC_LIBRARY);
            }
        });
    }

    private void configureXcodeForCpp(Project project, PBXTarget.ProductType productType) {
        // TODO: Assumes there's a single 'main' C++ component
        CppComponent component = project.getComponents().withType(CppComponent.class).getByName("main");
        FileCollection sources = component.getCppSource();
        xcode.getProject().getGroups().getSources().from(sources);

        FileCollection headers = component.getHeaderFiles();
        xcode.getProject().getGroups().getHeaders().from(headers);

        // TODO - Reuse the logic from `cpp-executable` or `cpp-library` to find the link task path
        // TODO - should use the _install_ task for an executable
        // TODO - should use the basename of the component to calculate the target names
        AbstractLinkTask linkDebug = (AbstractLinkTask) project.getTasks().getByName("linkDebug");
        AbstractLinkTask linkRelease = (AbstractLinkTask) project.getTasks().getByName("linkRelease");
        String targetName = StringUtils.capitalize(project.getName());
        XcodeTarget target = newTarget(targetName + " " + toString(productType), targetName, productType, toGradleCommand(project.getRootProject()), getBridgeTaskPath(project), linkDebug.getBinaryFile(), linkRelease.getBinaryFile(), sources);
        target.getHeaderSearchPaths().from(component.getDevelopmentBinary().getCompileIncludePath());
        xcode.getProject().addTarget(target);

        createSchemeTask(project.getTasks(), targetName + " " + toString(productType), xcode.getProject());
    }

    private static GenerateSchemeFileTask createSchemeTask(TaskContainer tasks, String schemeName, DefaultXcodeProject xcodeProject) {
        // TODO - capitalise the target name in the task name
        // TODO - don't create a launch target for a library
        String name = "xcodeScheme" + schemeName.replaceAll(" ", "");
        GenerateSchemeFileTask schemeFileTask = tasks.maybeCreate(name, GenerateSchemeFileTask.class);
        schemeFileTask.setXcodeProject(xcodeProject);
        schemeFileTask.setOutputFile(new File(xcodeProject.getLocationDir(), "xcshareddata/xcschemes/" + schemeName + ".xcscheme"));
        return schemeFileTask;
    }

    private static String toGradleCommand(Project project) {
        Gradle gradle = project.getGradle();
        Optional<String> gradleWrapperPath = Optional.absent();
        if (project.file("gradlew").exists()) {
            gradleWrapperPath = Optional.of(project.file("gradlew").getAbsolutePath());
        }

        if (gradle.getGradleHomeDir() != null) {
            if (gradleWrapperPath.isPresent() && gradle.getGradleHomeDir().getAbsolutePath().startsWith(gradle.getGradleUserHomeDir().getAbsolutePath())) {
                return gradleWrapperPath.get();
            }
            return gradle.getGradleHomeDir().getAbsolutePath() + "/bin/gradle";
        }

        return gradleWrapperPath.or("gradle");
    }

    private XcodeTarget newTarget(String name, String productName, PBXTarget.ProductType productType, String gradleCommand, String taskName, Provider<? extends FileSystemLocation> debugBinaryFile, Provider<? extends FileSystemLocation> releaseBinaryFile, FileCollection sources) {
        String id = gidGenerator.generateGid("PBXLegacyTarget", name.hashCode());
        XcodeTarget target = objectFactory.newInstance(XcodeTarget.class, name, id, debugBinaryFile, releaseBinaryFile);
        target.setTaskName(taskName);
        target.setGradleCommand(gradleCommand);
        target.setOutputFileType(toFileType(productType));
        target.setProductType(productType);
        target.setProductName(productName);
        target.getSources().setFrom(sources);

        return target;
    }

    private static File toXcodeWorkspacePackageDir(Project project) {
        return project.file(project.getName() + ".xcworkspace");
    }

    private static String toString(PBXTarget.ProductType productType) {
        if (PBXTarget.ProductType.TOOL.equals(productType)) {
            return "Executable";
        } else if (PBXTarget.ProductType.DYNAMIC_LIBRARY.equals(productType)) {
            return "SharedLibrary";
        } else if (PBXTarget.ProductType.UNIT_TEST.equals(productType)) {
            return "XCTestBundle";
        } else {
            return "";
        }
    }

    private static String toFileType(PBXTarget.ProductType productType) {
        if (PBXTarget.ProductType.TOOL.equals(productType)) {
            return FileTypes.MACH_O_EXECUTABLE.identifier;
        } else if (PBXTarget.ProductType.DYNAMIC_LIBRARY.equals(productType)) {
            return FileTypes.MACH_O_DYNAMIC_LIBRARY.identifier;
        } else {
            return "compiled";
        }
    }

    private static boolean isRoot(Project project) {
        return project.getParent() == null;
    }

    private static XcodeExtension xcodeModelFor(Project project) {
        return project.getExtensions().getByType(XcodeExtension.class);
    }

    private void registerXcodeProjectArtifact(Project project, GenerateXcodeProjectFileTask projectTask) {
        ProjectLocalComponentProvider projectComponentProvider = ((ProjectInternal) project).getServices().get(ProjectLocalComponentProvider.class);
        ProjectComponentIdentifier projectId = newProjectId(project);
        projectComponentProvider.registerAdditionalArtifact(projectId, createXcodeProjectArtifact(projectId, xcodeModelFor(project), projectTask));
    }

    private static LocalComponentArtifactMetadata createXcodeProjectArtifact(ProjectComponentIdentifier projectId, XcodeExtension xcode, Task projectTask) {
        XcodeProject xcodeProject = xcode.getProject();
        PublishArtifact publishArtifact = new XcodeProjectArtifact(xcodeProject, projectTask);
        return new PublishArtifactLocalArtifactMetadata(projectId, publishArtifact);
    }

    private void addIncludedBuildToWorkspace(final Project project, GenerateXcodeWorkspaceFileTask workspaceTask) {
        final ServiceRegistry serviceRegistry = ((ProjectInternal) project).getServices();
        workspaceTask.dependsOn(new Callable<List<TaskDependency>>() {
            @Override
            public List<TaskDependency> call() throws Exception {
                return CollectionUtils.collect(
                    allXcodeprojArtifactsInComposite(serviceRegistry),
                    new Transformer<TaskDependency, LocalComponentArtifactMetadata>() {
                        @Override
                        public TaskDependency transform(LocalComponentArtifactMetadata metadata) {
                            return metadata.getBuildDependencies();
                        }
                    });
            }
        });

        workspaceTask.setXcodeProjectLocations(project.files(new Callable<Iterable<File>>() {
            @Override
            public Iterable<File> call() throws Exception {
                return CollectionUtils.collect(
                    allXcodeprojArtifactsInComposite(serviceRegistry),
                    new Transformer<File, LocalComponentArtifactMetadata>() {
                        @Override
                        public File transform(LocalComponentArtifactMetadata metadata) {
                            return metadata.getFile();
                        }
                    });
            }
        }));
    }

    private static List<LocalComponentArtifactMetadata> allXcodeprojArtifactsInComposite(ServiceRegistry serviceRegistry) {
        List<LocalComponentArtifactMetadata> artifactMetadata = Lists.newArrayList();
        ProjectPathRegistry projectPathRegistry = serviceRegistry.get(ProjectPathRegistry.class);
        LocalComponentRegistry localComponentRegistry = serviceRegistry.get(LocalComponentRegistry.class);

        for (Path projectPath : projectPathRegistry.getAllProjectPaths()) {
            ProjectComponentIdentifier projectId = projectPathRegistry.getProjectComponentIdentifier(projectPath);
            LocalComponentArtifactMetadata xcodeprojArtifact = localComponentRegistry.findAdditionalArtifact(projectId, "xcodeproj");
            if (xcodeprojArtifact != null) {
                artifactMetadata.add(xcodeprojArtifact);
            }
        }

        return artifactMetadata;
    }

    private static class XcodeProjectArtifact extends DefaultPublishArtifact {
        private final DefaultXcodeProject xcodeProject;

        public XcodeProjectArtifact(XcodeProject xcodeProject, Object... tasks) {
            super(null, "xcodeproj", "xcodeproj", null, null, null, tasks);
            this.xcodeProject = (DefaultXcodeProject) xcodeProject;
        }

        @Override
        public String getName() {
            String fileName = xcodeProject.getLocationDir().getName();
            return fileName.substring(0, fileName.length() - ".xcodeproj".length());
        }

        @Override
        public File getFile() {
            return xcodeProject.getLocationDir();
        }
    }

    private static class XcodeBridge implements Action<String> {
        private final DefaultXcodeProject xcodeProject;
        private final Project project;

        public XcodeBridge(DefaultXcodeProject xcodeProject, Project project) {
            this.xcodeProject = xcodeProject;
            this.project = project;
        }

        @Override
        public void execute(String taskName) {
            if (taskName.startsWith("_xcode")) {
                Task bridgeTask = project.getTasks().create(taskName);
                String action = System.getenv("ACTION");
                if (action.equals("clean")) {
                    bridgeTask.dependsOn("clean");
                } else if ("".equals(action) || "build".equals(action)) {
                    final XcodeTarget target = findXcodeTarget();
                    if (target.isUnitTest()) {
                        bridgeTestExecution(bridgeTask, target);
                    } else {
                        bridgeProductBuild(bridgeTask, target);

                    }
                } else {
                    throw new GradleException("Unrecognized bridge action from Xcode '" + action + "'");
                }
            }
        }

        private XcodeTarget findXcodeTarget() {
            final String productName = System.getenv("PRODUCT_NAME");
            final XcodeTarget target = CollectionUtils.findFirst(xcodeProject.getTargets(), new Spec<XcodeTarget>() {
                @Override
                public boolean isSatisfiedBy(XcodeTarget target) {
                    return target.getProductName().equals(productName);
                }
            });
            if (target == null) {
                throw new GradleException("Unknown Xcode target '" + productName + "', do you need to re-generate Xcode configuration?");
            }
            return target;
        }

        private void bridgeProductBuild(Task bridgeTask, XcodeTarget target) {
            // Library or executable
            final String configuration = System.getenv("CONFIGURATION");
            if (configuration.equals(DefaultXcodeProject.BUILD_DEBUG)) {
                bridgeTask.dependsOn(target.getDebugOutputFile());
            } else {
                bridgeTask.dependsOn(target.getReleaseOutputFile());
            }
        }

        private void bridgeTestExecution(Task bridgeTask, final XcodeTarget target) {
            // XcTest executable
            // Sync the binary to the BUILT_PRODUCTS_DIR, otherwise Xcode won't find any tests
            final String builtProductsPath = System.getenv("BUILT_PRODUCTS_DIR");
            final Sync syncTask = project.getTasks().create("syncBundleToXcodeBuiltProductDir", Sync.class, new Action<Sync>() {
                @Override
                public void execute(Sync task) {
                    task.from(target.getDebugOutputFile());
                    task.into(builtProductsPath);
                }
            });
            bridgeTask.dependsOn(syncTask);
        }
    }
}
