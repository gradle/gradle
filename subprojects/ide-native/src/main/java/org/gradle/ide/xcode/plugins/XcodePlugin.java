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

import org.apache.commons.lang.StringUtils;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.ArtifactView;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.component.SoftwareComponent;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.Delete;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.ide.xcode.XcodeExtension;
import org.gradle.ide.xcode.XcodeRootExtension;
import org.gradle.ide.xcode.internal.DefaultXcodeExtension;
import org.gradle.ide.xcode.internal.DefaultXcodeProject;
import org.gradle.ide.xcode.internal.DefaultXcodeRootExtension;
import org.gradle.ide.xcode.internal.DefaultXcodeWorkspace;
import org.gradle.ide.xcode.internal.XcodeProjectMetadata;
import org.gradle.ide.xcode.internal.XcodePropertyAdapter;
import org.gradle.ide.xcode.internal.XcodeTarget;
import org.gradle.ide.xcode.internal.xcodeproj.GidGenerator;
import org.gradle.ide.xcode.internal.xcodeproj.PBXTarget;
import org.gradle.ide.xcode.tasks.GenerateSchemeFileTask;
import org.gradle.ide.xcode.tasks.GenerateWorkspaceSettingsFileTask;
import org.gradle.ide.xcode.tasks.GenerateXcodeProjectFileTask;
import org.gradle.ide.xcode.tasks.GenerateXcodeWorkspaceFileTask;
import org.gradle.internal.Actions;
import org.gradle.language.cpp.CppBinary;
import org.gradle.language.cpp.CppExecutable;
import org.gradle.language.cpp.CppLibrary;
import org.gradle.language.cpp.CppSharedLibrary;
import org.gradle.language.cpp.CppStaticLibrary;
import org.gradle.language.cpp.ProductionCppComponent;
import org.gradle.language.cpp.internal.DefaultCppBinary;
import org.gradle.language.cpp.plugins.CppApplicationPlugin;
import org.gradle.language.cpp.plugins.CppLibraryPlugin;
import org.gradle.language.swift.ProductionSwiftComponent;
import org.gradle.language.swift.SwiftBinary;
import org.gradle.language.swift.SwiftExecutable;
import org.gradle.language.swift.SwiftSharedLibrary;
import org.gradle.language.swift.SwiftStaticLibrary;
import org.gradle.language.swift.internal.DefaultSwiftBinary;
import org.gradle.language.swift.plugins.SwiftApplicationPlugin;
import org.gradle.language.swift.plugins.SwiftLibraryPlugin;
import org.gradle.nativeplatform.test.xctest.SwiftXCTestSuite;
import org.gradle.nativeplatform.test.xctest.plugins.XCTestConventionPlugin;
import org.gradle.plugins.ide.internal.IdeArtifactRegistry;
import org.gradle.plugins.ide.internal.IdePlugin;
import org.gradle.util.internal.CollectionUtils;

import javax.inject.Inject;
import java.io.File;

/**
 * A plugin for creating a XCode project for a gradle project.
 *
 * @since 4.2
 */
public abstract class XcodePlugin extends IdePlugin {
    private final GidGenerator gidGenerator;
    private final ObjectFactory objectFactory;
    private final IdeArtifactRegistry artifactRegistry;
    private DefaultXcodeProject xcodeProject;

    @Inject
    public XcodePlugin(GidGenerator gidGenerator, ObjectFactory objectFactory, IdeArtifactRegistry artifactRegistry) {
        this.gidGenerator = gidGenerator;
        this.objectFactory = objectFactory;
        this.artifactRegistry = artifactRegistry;
    }

    @Override
    protected String getLifecycleTaskName() {
        return "xcode";
    }

    @Override
    protected void onApply(final Project project) {
        TaskProvider<? extends Task> lifecycleTask = getLifecycleTask();
        lifecycleTask.configure(withDescription("Generates XCode project files (pbxproj, xcworkspace, xcscheme)"));

        if (isRoot()) {
            DefaultXcodeRootExtension xcode = (DefaultXcodeRootExtension) project.getExtensions().create(XcodeRootExtension.class, "xcode", DefaultXcodeRootExtension.class, objectFactory);
            xcodeProject = xcode.getProject();
            final GenerateXcodeWorkspaceFileTask workspaceTask = createWorkspaceTask(project, xcode.getWorkspace());
            lifecycleTask.configure(dependsOn(workspaceTask));
            addWorkspace(xcode.getWorkspace());
        } else {
            DefaultXcodeExtension xcode = (DefaultXcodeExtension) project.getExtensions().create(XcodeExtension.class, "xcode", DefaultXcodeExtension.class, objectFactory);
            xcodeProject = xcode.getProject();
        }

        xcodeProject.setLocationDir(project.file(project.getName() + ".xcodeproj"));

        GenerateXcodeProjectFileTask projectTask = createProjectTask((ProjectInternal) project);
        lifecycleTask.configure(dependsOn(projectTask));

        project.getTasks().addRule("Xcode bridge tasks begin with _xcode. Do not call these directly.", new XcodeBridge(xcodeProject, project));

        configureForSwiftPlugin(project);
        configureForCppPlugin(project);

        includeBuildFilesInProject(project);
        configureXcodeCleanTask(project);
    }

    private void includeBuildFilesInProject(Project project) {
        // TODO: Add other build like files `build.gradle.kts`, `settings.gradle(.kts)`, other `.gradle`, `gradle.properties`
        if (project.getBuildFile().exists()) {
            xcodeProject.getGroups().getRoot().from(project.getBuildFile());
        }
    }

    private void configureXcodeCleanTask(Project project) {
        Delete cleanTask = project.getTasks().create("cleanXcodeProject", Delete.class);
        cleanTask.delete(xcodeProject.getLocationDir());
        if (isRoot()) {
            cleanTask.delete(project.file(project.getName() + ".xcworkspace"));
        }
        getCleanTask().configure(Actions.composite(withDescription("Cleans XCode project files (xcodeproj)"), dependsOn(cleanTask)));
    }

    private GenerateXcodeProjectFileTask createProjectTask(final ProjectInternal project) {
        File xcodeProjectPackageDir = xcodeProject.getLocationDir();

        GenerateWorkspaceSettingsFileTask workspaceSettingsFileTask = project.getTasks().create("xcodeProjectWorkspaceSettings", GenerateWorkspaceSettingsFileTask.class);
        workspaceSettingsFileTask.setOutputFile(new File(xcodeProjectPackageDir, "project.xcworkspace/xcshareddata/WorkspaceSettings.xcsettings"));

        GenerateXcodeProjectFileTask projectFileTask = project.getTasks().create("xcodeProject", GenerateXcodeProjectFileTask.class);
        projectFileTask.dependsOn(workspaceSettingsFileTask);
        projectFileTask.dependsOn(xcodeProject.getTaskDependencies());
        projectFileTask.dependsOn(project.getTasks().withType(GenerateSchemeFileTask.class));
        projectFileTask.setXcodeProject(xcodeProject);
        projectFileTask.setOutputFile(new File(xcodeProjectPackageDir, "project.pbxproj"));

        artifactRegistry.registerIdeProject(new XcodeProjectMetadata(xcodeProject, projectFileTask));

        return projectFileTask;
    }

    private GenerateXcodeWorkspaceFileTask createWorkspaceTask(Project project, DefaultXcodeWorkspace workspace) {
        File xcodeWorkspacePackageDir = project.file(project.getName() + ".xcworkspace");
        workspace.getLocation().set(xcodeWorkspacePackageDir);

        GenerateWorkspaceSettingsFileTask workspaceSettingsFileTask = project.getTasks().create("xcodeWorkspaceWorkspaceSettings", GenerateWorkspaceSettingsFileTask.class);
        workspaceSettingsFileTask.setOutputFile(new File(xcodeWorkspacePackageDir, "xcshareddata/WorkspaceSettings.xcsettings"));

        GenerateXcodeWorkspaceFileTask workspaceFileTask = project.getTasks().create("xcodeWorkspace", GenerateXcodeWorkspaceFileTask.class);
        workspaceFileTask.dependsOn(workspaceSettingsFileTask);
        workspaceFileTask.setOutputFile(new File(xcodeWorkspacePackageDir, "contents.xcworkspacedata"));
        workspaceFileTask.setXcodeProjectLocations(artifactRegistry.getIdeProjectFiles(XcodeProjectMetadata.class));

        return workspaceFileTask;
    }

    private String getBridgeTaskPath(Project project) {
        String projectPath = "";
        if (!isRoot()) {
            projectPath = project.getPath();
        }
        return projectPath + ":_xcode__${ACTION}_${PRODUCT_NAME}_${CONFIGURATION}";
    }

    private void configureForSwiftPlugin(final Project project) {
        project.getPlugins().withType(SwiftApplicationPlugin.class, new Action<SwiftApplicationPlugin>() {
            @Override
            public void execute(SwiftApplicationPlugin plugin) {
                configureXcodeForSwift(project);
            }
        });

        project.getPlugins().withType(SwiftLibraryPlugin.class, new Action<SwiftLibraryPlugin>() {
            @Override
            public void execute(SwiftLibraryPlugin plugin) {
                configureXcodeForSwift(project);
            }
        });

        project.getPlugins().withType(XCTestConventionPlugin.class, new Action<XCTestConventionPlugin>() {
            @Override
            public void execute(XCTestConventionPlugin plugin) {
                configureXcodeForXCTest(project);
            }
        });
    }

    private void configureXcodeForXCTest(final Project project) {
        project.afterEvaluate(new Action<Project>() {
            @Override
            public void execute(Project project) {
                SwiftXCTestSuite component = project.getExtensions().getByType(SwiftXCTestSuite.class);
                FileCollection sources = component.getSwiftSource();
                xcodeProject.getGroups().getTests().from(sources);

                String targetName = component.getModule().get();
                final XcodeTarget target = newTarget(targetName, component.getModule().get(), toGradleCommand(project), getBridgeTaskPath(project), sources);
                target.getSwiftSourceCompatibility().convention(component.getSourceCompatibility());
                if (component.getTestBinary().isPresent()) {
                    target.addBinary(DefaultXcodeProject.BUILD_DEBUG, component.getTestBinary().get().getInstallDirectory(), component.getTestBinary().get().getTargetMachine().getArchitecture().getName());
                    target.addBinary(DefaultXcodeProject.BUILD_RELEASE, component.getTestBinary().get().getInstallDirectory(), component.getTestBinary().get().getTargetMachine().getArchitecture().getName());
                    target.setProductType(PBXTarget.ProductType.UNIT_TEST);
                    target.getCompileModules().from(component.getTestBinary().get().getCompileModules());
                    target.addTaskDependency(filterArtifactsFromImplicitBuilds(((DefaultSwiftBinary) component.getTestBinary().get()).getImportPathConfiguration()).getBuildDependencies());
                }
                component.getBinaries().whenElementFinalized(new Action<SwiftBinary>() {
                    @Override
                    public void execute(SwiftBinary swiftBinary) {
                        target.getSwiftSourceCompatibility().set(swiftBinary.getTargetPlatform().getSourceCompatibility());
                    }
                });
                xcodeProject.addTarget(target);
            }
        });
    }

    private FileCollection filterArtifactsFromImplicitBuilds(Configuration configuration) {
        return configuration.getIncoming().artifactView(fromSourceDependency()).getArtifacts().getArtifactFiles();
    }

    private void configureXcodeForSwift(final Project project) {
        project.afterEvaluate(new Action<Project>() {
            @Override
            public void execute(final Project project) {
                // TODO: Assumes there's a single 'main' Swift component
                final ProductionSwiftComponent component = project.getComponents().withType(ProductionSwiftComponent.class).getByName("main");

                FileCollection sources = component.getSwiftSource();
                xcodeProject.getGroups().getSources().from(sources);

                // TODO - should use the _install_ task for an executable
                final String targetName = component.getModule().get();
                final XcodeTarget target = newTarget(targetName, component.getModule().get(), toGradleCommand(project), getBridgeTaskPath(project), sources);
                target.getDefaultConfigurationName().set(component.getDevelopmentBinary().map(devBinary -> toBuildConfigurationName(component, devBinary)));
                component.getBinaries().whenElementFinalized(new Action<SwiftBinary>() {
                    @Override
                    public void execute(SwiftBinary swiftBinary) {
                        if (swiftBinary instanceof SwiftExecutable) {
                            target.addBinary(toBuildConfigurationName(component, swiftBinary), ((SwiftExecutable) swiftBinary).getDebuggerExecutableFile(), swiftBinary.getTargetMachine().getArchitecture().getName());
                            target.setProductType(PBXTarget.ProductType.TOOL);
                        } else if (swiftBinary instanceof SwiftSharedLibrary) {
                            target.addBinary(toBuildConfigurationName(component, swiftBinary), ((SwiftSharedLibrary) swiftBinary).getRuntimeFile(), swiftBinary.getTargetMachine().getArchitecture().getName());
                            target.setProductType(PBXTarget.ProductType.DYNAMIC_LIBRARY);
                        } else if (swiftBinary instanceof SwiftStaticLibrary) {
                            target.addBinary(toBuildConfigurationName(component, swiftBinary), ((SwiftStaticLibrary) swiftBinary).getLinkFile(), swiftBinary.getTargetMachine().getArchitecture().getName());
                            target.setProductType(PBXTarget.ProductType.STATIC_LIBRARY);
                        }
                        target.getSwiftSourceCompatibility().set(swiftBinary.getTargetPlatform().getSourceCompatibility());

                        if (swiftBinary == component.getDevelopmentBinary().get()) {
                            target.getCompileModules().from(component.getDevelopmentBinary().get().getCompileModules());
                            target.addTaskDependency(filterArtifactsFromImplicitBuilds(((DefaultSwiftBinary) component.getDevelopmentBinary().get()).getImportPathConfiguration()).getBuildDependencies());

                            createSchemeTask(project.getTasks(), targetName, xcodeProject);
                        }
                    }
                });
                xcodeProject.addTarget(target);
            }
        });
    }

    private String toBuildConfigurationName(SoftwareComponent component, SoftwareComponent binary) {
        String result = binary.getName().replace(component.getName(), "");
        if (binary instanceof SwiftSharedLibrary || binary instanceof CppSharedLibrary) {
            return result.replace("Shared", "");
        } else if (binary instanceof SwiftStaticLibrary || binary instanceof  CppStaticLibrary) {
            return result.replace("Static", "");
        }
        return result;
    }

    private void configureForCppPlugin(final Project project) {
        project.getPlugins().withType(CppApplicationPlugin.class, new Action<CppApplicationPlugin>() {
            @Override
            public void execute(CppApplicationPlugin plugin) {
                configureXcodeForCpp(project);
            }
        });

        project.getPlugins().withType(CppLibraryPlugin.class, new Action<CppLibraryPlugin>() {
            @Override
            public void execute(CppLibraryPlugin plugin) {
                configureXcodeForCpp(project);
            }
        });
    }

    private void configureXcodeForCpp(Project project) {
        project.afterEvaluate(new Action<Project>() {
            @Override
            public void execute(final Project project) {
                // TODO: Assumes there's a single 'main' C++ component
                final ProductionCppComponent component = project.getComponents().withType(ProductionCppComponent.class).getByName("main");

                FileCollection sources = component.getCppSource();
                xcodeProject.getGroups().getSources().from(sources);

                FileCollection headers = component.getHeaderFiles();
                xcodeProject.getGroups().getHeaders().from(headers);

                // TODO - should use the _install_ task for an executable
                final String targetName = StringUtils.capitalize(component.getBaseName().get());
                final XcodeTarget target = newTarget(targetName, targetName, toGradleCommand(project), getBridgeTaskPath(project), sources);
                target.getDefaultConfigurationName().set(component.getDevelopmentBinary().map(devBinary -> toBuildConfigurationName(component, devBinary)));
                component.getBinaries().whenElementFinalized(new Action<CppBinary>() {
                    @Override
                    public void execute(CppBinary cppBinary) {
                        if (cppBinary instanceof CppExecutable) {
                            target.addBinary(toBuildConfigurationName(component, cppBinary), ((CppExecutable) cppBinary).getDebuggerExecutableFile(), cppBinary.getTargetMachine().getArchitecture().getName());
                            target.setProductType(PBXTarget.ProductType.TOOL);
                        } else if (cppBinary instanceof CppSharedLibrary) {
                            target.addBinary(toBuildConfigurationName(component, cppBinary), ((CppSharedLibrary) cppBinary).getRuntimeFile(), cppBinary.getTargetMachine().getArchitecture().getName());
                            target.setProductType(PBXTarget.ProductType.DYNAMIC_LIBRARY);
                        } else if (cppBinary instanceof CppStaticLibrary) {
                            target.addBinary(toBuildConfigurationName(component, cppBinary), ((CppStaticLibrary) cppBinary).getLinkFile(), cppBinary.getTargetMachine().getArchitecture().getName());
                            target.setProductType(PBXTarget.ProductType.STATIC_LIBRARY);
                        }

                        if (cppBinary == component.getDevelopmentBinary().get()) {
                            target.getHeaderSearchPaths().from(component.getDevelopmentBinary().get().getCompileIncludePath());
                            target.getTaskDependencies().add(filterArtifactsFromImplicitBuilds(((DefaultCppBinary) component.getDevelopmentBinary().get()).getIncludePathConfiguration()).getBuildDependencies());

                            createSchemeTask(project.getTasks(), targetName, xcodeProject);
                        }
                    }
                });
                target.getHeaderSearchPaths().from(component.getPrivateHeaderDirs());
                if (component instanceof CppLibrary) {
                    target.getHeaderSearchPaths().from(((CppLibrary) component).getPublicHeaderDirs());
                }
                xcodeProject.addTarget(target);
            }
        });
    }

    private static GenerateSchemeFileTask createSchemeTask(TaskContainer tasks, String schemeName, DefaultXcodeProject xcodeProject) {
        // TODO - capitalise the target name in the task name
        // TODO - don't create a launch target for a library
        String name = "xcodeScheme";
        GenerateSchemeFileTask schemeFileTask = tasks.maybeCreate(name, GenerateSchemeFileTask.class);
        schemeFileTask.setXcodeProject(xcodeProject);
        schemeFileTask.setOutputFile(new File(xcodeProject.getLocationDir(), "xcshareddata/xcschemes/" + schemeName + ".xcscheme"));
        return schemeFileTask;
    }

    private XcodeTarget newTarget(String name, String productName, String gradleCommand, String taskName, FileCollection sources) {
        String id = gidGenerator.generateGid("PBXLegacyTarget", name.hashCode());
        XcodeTarget target = objectFactory.newInstance(XcodeTarget.class, name, id);
        target.setTaskName(taskName);
        target.setGradleCommand(gradleCommand);
        target.setProductName(productName);
        target.getSources().setFrom(sources);

        return target;
    }

    private static class XcodeBridge implements Action<String> {
        private final DefaultXcodeProject xcodeProject;
        private final Project project;
        private final XcodePropertyAdapter xcodePropertyAdapter;

        XcodeBridge(DefaultXcodeProject xcodeProject, Project project) {
            this.xcodeProject = xcodeProject;
            this.project = project;
            this.xcodePropertyAdapter = new XcodePropertyAdapter(project);
        }

        @Override
        public void execute(String taskName) {
            if (taskName.startsWith("_xcode")) {
                Task bridgeTask = project.getTasks().create(taskName);
                String action = xcodePropertyAdapter.getAction();
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
            final String productName = xcodePropertyAdapter.getProductName();
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
            final String configuration = xcodePropertyAdapter.getConfiguration();
            bridgeTask.dependsOn(target.getBinaries().stream().filter(it -> it.getBuildConfigurationName().equals(configuration)).findFirst().get().getOutputFile());
        }

        private void bridgeTestExecution(Task bridgeTask, final XcodeTarget target) {
            // XCTest executable
            // Sync the binary to the BUILT_PRODUCTS_DIR, otherwise Xcode won't find any tests
            final String builtProductsPath = xcodePropertyAdapter.getBuiltProductsDir();
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

    private Action<ArtifactView.ViewConfiguration> fromSourceDependency() {
        return new Action<ArtifactView.ViewConfiguration>() {
            @Override
            public void execute(ArtifactView.ViewConfiguration viewConfiguration) {
                viewConfiguration.componentFilter(isSourceDependency());
            }
        };
    }

    private Spec<ComponentIdentifier> isSourceDependency() {
        return new Spec<ComponentIdentifier>() {
            @Override
            public boolean isSatisfiedBy(ComponentIdentifier id) {
                if (id instanceof ProjectComponentIdentifier) {
                    // Include as binary when the target project is not included in the workspace
                    return artifactRegistry.getIdeProject(XcodeProjectMetadata.class, (ProjectComponentIdentifier) id) == null;
                }
                return false;
            }
        };
    }
}
