/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.testfixtures.internal;

import org.gradle.api.Project;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.StartParameterInternal;
import org.gradle.api.internal.artifacts.DefaultBuildIdentifier;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.temp.DefaultTemporaryFileProvider;
import org.gradle.api.internal.file.temp.TemporaryFileProvider;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectState;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.initialization.BuildRequestMetaData;
import org.gradle.initialization.DefaultBuildCancellationToken;
import org.gradle.initialization.DefaultBuildRequestMetaData;
import org.gradle.initialization.DefaultProjectDescriptor;
import org.gradle.initialization.LegacyTypesSupport;
import org.gradle.initialization.NoOpBuildEventConsumer;
import org.gradle.initialization.ProjectDescriptorRegistry;
import org.gradle.internal.Factory;
import org.gradle.internal.FileUtils;
import org.gradle.internal.Pair;
import org.gradle.internal.SystemProperties;
import org.gradle.internal.build.AbstractBuildState;
import org.gradle.internal.build.BuildLifecycleController;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.internal.build.BuildWorkGraph;
import org.gradle.internal.build.RootBuildState;
import org.gradle.internal.buildtree.BuildTreeLifecycleController;
import org.gradle.internal.buildtree.BuildTreeModelControllerServices;
import org.gradle.internal.buildtree.BuildTreeState;
import org.gradle.internal.buildtree.RunTasksRequirements;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.composite.IncludedBuildInternal;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.logging.services.LoggingServiceRegistry;
import org.gradle.internal.nativeintegration.services.NativeServices;
import org.gradle.internal.resources.DefaultResourceLockCoordinationService;
import org.gradle.internal.resources.ResourceLockCoordinationService;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.ServiceRegistryBuilder;
import org.gradle.internal.service.scopes.GradleUserHomeScopeServiceRegistry;
import org.gradle.internal.session.BuildSessionState;
import org.gradle.internal.session.CrossBuildSessionState;
import org.gradle.internal.time.Time;
import org.gradle.internal.work.DefaultWorkerLeaseService;
import org.gradle.internal.work.WorkerLeaseRegistry;
import org.gradle.internal.work.WorkerLeaseService;
import org.gradle.util.Path;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Collections;
import java.util.Set;
import java.util.function.Function;

import static org.gradle.internal.concurrent.CompositeStoppable.stoppable;

public class ProjectBuilderImpl {
    private static ServiceRegistry globalServices;

    public Project createChildProject(String name, Project parent, File projectDir) {
        ProjectInternal parentProject = (ProjectInternal) parent;
        ProjectDescriptorRegistry descriptorRegistry = parentProject.getServices().get(ProjectDescriptorRegistry.class);
        DefaultProjectDescriptor parentDescriptor = descriptorRegistry.getProject(parentProject.getPath());

        projectDir = (projectDir != null) ? projectDir.getAbsoluteFile() : new File(parentProject.getProjectDir(), name);
        DefaultProjectDescriptor projectDescriptor = new DefaultProjectDescriptor(parentDescriptor, name, projectDir, descriptorRegistry, parentProject.getServices().get(FileResolver.class));
        descriptorRegistry.addProject(projectDescriptor);

        ProjectState projectState = parentProject.getServices().get(ProjectStateRegistry.class).registerProject(parentProject.getServices().get(BuildState.class), projectDescriptor);
        projectState.createMutableModel(parentProject.getClassLoaderScope().createChild("project-" + name), parentProject.getBaseClassLoaderScope());
        ProjectInternal project = projectState.getMutableModel();

        // Lock the project, these won't ever be released as ProjectBuilder has no lifecycle
        ResourceLockCoordinationService coordinationService = project.getServices().get(ResourceLockCoordinationService.class);
        coordinationService.withStateLock(DefaultResourceLockCoordinationService.lock(project.getOwner().getAccessLock()));

        return project;
    }

    public ProjectInternal createProject(String name, File inputProjectDir, File gradleUserHomeDir) {

        final File projectDir = prepareProjectDir(inputProjectDir);
        final File homeDir = new File(projectDir, "gradleHome");
        File userHomeDir = gradleUserHomeDir == null ? new File(projectDir, "userHome") : FileUtils.canonicalize(gradleUserHomeDir);
        StartParameterInternal startParameter = new StartParameterInternal();
        startParameter.setGradleUserHomeDir(userHomeDir);
        NativeServices.initializeOnDaemon(userHomeDir);

        final ServiceRegistry globalServices = getGlobalServices();

        BuildRequestMetaData buildRequestMetaData = new DefaultBuildRequestMetaData(Time.currentTimeMillis());
        CrossBuildSessionState crossBuildSessionState = new CrossBuildSessionState(globalServices, startParameter);
        GradleUserHomeScopeServiceRegistry userHomeServices = userHomeServicesOf(globalServices);
        BuildSessionState buildSessionState = new BuildSessionState(userHomeServices, crossBuildSessionState, startParameter, buildRequestMetaData, ClassPath.EMPTY, new DefaultBuildCancellationToken(), buildRequestMetaData.getClient(), new NoOpBuildEventConsumer());
        BuildTreeModelControllerServices.Supplier modelServices = buildSessionState.getServices().get(BuildTreeModelControllerServices.class).servicesForBuildTree(new RunTasksRequirements(startParameter));
        BuildTreeState buildTreeState = new BuildTreeState(buildSessionState.getServices(), modelServices);
        TestBuildScopeServices buildServices = new TestBuildScopeServices(buildTreeState.getServices(), homeDir, startParameter);
        TestRootBuild build = new TestRootBuild(projectDir, buildServices);

        buildServices.get(BuildStateRegistry.class).attachRootBuild(build);

        GradleInternal gradle = build.getMutableModel();
        gradle.setIncludedBuilds(Collections.emptyList());

        ProjectDescriptorRegistry projectDescriptorRegistry = buildServices.get(ProjectDescriptorRegistry.class);
        DefaultProjectDescriptor projectDescriptor = new DefaultProjectDescriptor(null, name, projectDir, projectDescriptorRegistry, buildServices.get(FileResolver.class));
        projectDescriptorRegistry.addProject(projectDescriptor);

        ClassLoaderScope baseScope = gradle.getClassLoaderScope();
        ClassLoaderScope rootProjectScope = baseScope.createChild("root-project");

        ProjectStateRegistry projectStateRegistry = buildServices.get(ProjectStateRegistry.class);
        ProjectState projectState = projectStateRegistry.registerProject(build, projectDescriptor);
        projectState.createMutableModel(rootProjectScope, baseScope);
        ProjectInternal project = projectState.getMutableModel();

        gradle.setRootProject(project);
        gradle.setDefaultProject(project);

        // Take a root worker lease and lock the project, these won't ever be released as ProjectBuilder has no lifecycle
        ResourceLockCoordinationService coordinationService = buildServices.get(ResourceLockCoordinationService.class);
        WorkerLeaseService workerLeaseService = buildServices.get(WorkerLeaseService.class);
        WorkerLeaseRegistry.WorkerLease workerLease = workerLeaseService.getWorkerLease();
        coordinationService.withStateLock(DefaultResourceLockCoordinationService.lock(workerLease, project.getOwner().getAccessLock()));

        project.getExtensions().getExtraProperties().set(
            "ProjectBuilder.stoppable",
            stoppable(
                (Stoppable) workerLeaseService::releaseCurrentProjectLocks,
                (Stoppable) ((DefaultWorkerLeaseService) workerLeaseService)::releaseCurrentResourceLocks,
                buildServices,
                buildTreeState,
                buildSessionState,
                crossBuildSessionState
            )
        );

        return project;
    }

    public static void stop(Project rootProject) {
        ((Stoppable) rootProject.getExtensions().getExtraProperties().get("ProjectBuilder.stoppable")).stop();
    }

    private GradleUserHomeScopeServiceRegistry userHomeServicesOf(ServiceRegistry globalServices) {
        return globalServices.get(GradleUserHomeScopeServiceRegistry.class);
    }

    public synchronized static ServiceRegistry getGlobalServices() {
        if (globalServices == null) {
            globalServices = createGlobalServices();
            // Inject missing interfaces to support the usage of plugins compiled with older Gradle versions.
            // A normal gradle build does this by adding the MixInLegacyTypesClassLoader to the class loader hierarchy.
            // In a test run, which is essentially a plain Java application, the classpath is flattened and injected
            // into the system class loader and there exists no Gradle class loader hierarchy in the running test. (See Implementation
            // in ApplicationClassesInSystemClassLoaderWorkerImplementationFactory, BootstrapSecurityManager and GradleWorkerMain.)
            // Thus, we inject the missing interfaces directly into the system class loader used to load all classes in the test.
            globalServices.get(LegacyTypesSupport.class).injectEmptyInterfacesIntoClassLoader(ProjectBuilderImpl.class.getClassLoader());
        }
        return globalServices;
    }

    private static ServiceRegistry createGlobalServices() {
        return ServiceRegistryBuilder
            .builder()
            .displayName("global services")
            .parent(LoggingServiceRegistry.newNestedLogging())
            .parent(NativeServices.getInstance())
            .provider(new TestGlobalScopeServices())
            .build();
    }

    public File prepareProjectDir(@Nullable final File projectDir) {
        if (projectDir != null) {
            return FileUtils.canonicalize(projectDir);
        }

        TemporaryFileProvider temporaryFileProvider = new DefaultTemporaryFileProvider(new Factory<File>() {
            @Override
            public File create() {
                String rootTmpDir = SystemProperties.getInstance().getWorkerTmpDir();
                if (rootTmpDir == null) {
                    @SuppressWarnings("deprecation")
                    String javaIoTmpDir = SystemProperties.getInstance().getJavaIoTmpDir();
                    rootTmpDir = javaIoTmpDir;
                }
                return FileUtils.canonicalize(new File(rootTmpDir));
            }
        });
        File tempDirectory = temporaryFileProvider.createTemporaryDirectory("gradle", "projectDir");
        // TODO deleteOnExit won't clean up non-empty directories (and it leaks memory for long-running processes).
        tempDirectory.deleteOnExit();
        return tempDirectory;
    }

    private static class TestRootBuild extends AbstractBuildState implements RootBuildState {
        private final File rootProjectDir;
        private final ProjectStateRegistry projectStateRegistry;
        private final GradleInternal gradle;

        public TestRootBuild(File rootProjectDir, TestBuildScopeServices buildServices) {
            this.rootProjectDir = rootProjectDir;
            buildServices.add(BuildState.class, this);
            this.projectStateRegistry = buildServices.get(ProjectStateRegistry.class);
            this.gradle = buildServices.get(GradleInternal.class);
        }

        @Override
        protected BuildLifecycleController getBuildController() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void ensureProjectsLoaded() {
        }

        @Override
        public BuildWorkGraph getWorkGraph() {
            throw new UnsupportedOperationException();
        }

        @Override
        protected ProjectStateRegistry getProjectStateRegistry() {
            return projectStateRegistry;
        }

        @Override
        public BuildIdentifier getBuildIdentifier() {
            return DefaultBuildIdentifier.ROOT;
        }

        @Override
        public Path getIdentityPath() {
            return Path.ROOT;
        }

        @Override
        public boolean isImplicitBuild() {
            return false;
        }

        @Override
        public Path getCurrentPrefixForProjectsInChildBuilds() {
            return Path.ROOT;
        }

        @Override
        public Path calculateIdentityPathForProject(Path projectPath) {
            return projectPath;
        }

        @Override
        public StartParameterInternal getStartParameter() {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T run(Function<? super BuildTreeLifecycleController, T> action) {
            throw new UnsupportedOperationException();
        }

        @Override
        public IncludedBuildInternal getModel() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Set<Pair<ModuleVersionIdentifier, ProjectComponentIdentifier>> getAvailableModules() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ProjectComponentIdentifier idToReferenceProjectFromAnotherBuild(ProjectComponentIdentifier identifier) {
            throw new UnsupportedOperationException();
        }

        @Override
        public File getBuildRootDir() {
            return rootProjectDir;
        }

        @Override
        public GradleInternal getMutableModel() {
            return gradle;
        }

        @Override
        public GradleInternal getBuild() {
            return gradle;
        }
    }
}
