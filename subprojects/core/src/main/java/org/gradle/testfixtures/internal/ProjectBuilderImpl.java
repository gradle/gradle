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

import org.gradle.StartParameter;
import org.gradle.api.Project;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.internal.BuildType;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.internal.StartParameterInternal;
import org.gradle.api.internal.artifacts.DefaultBuildIdentifier;
import org.gradle.api.internal.artifacts.DefaultProjectComponentIdentifier;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.TemporaryFileProvider;
import org.gradle.api.internal.file.TmpDirTemporaryFileProvider;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.internal.project.IProjectFactory;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.initialization.BuildRequestMetaData;
import org.gradle.initialization.DefaultBuildCancellationToken;
import org.gradle.initialization.DefaultBuildRequestMetaData;
import org.gradle.initialization.DefaultProjectDescriptor;
import org.gradle.initialization.LegacyTypesSupport;
import org.gradle.initialization.NestedBuildFactory;
import org.gradle.initialization.NoOpBuildEventConsumer;
import org.gradle.initialization.ProjectDescriptorRegistry;
import org.gradle.internal.FileUtils;
import org.gradle.internal.build.AbstractBuildState;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.internal.build.RootBuildState;
import org.gradle.internal.buildtree.BuildTreeState;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.invocation.BuildController;
import org.gradle.internal.logging.services.LoggingServiceRegistry;
import org.gradle.internal.nativeintegration.services.NativeServices;
import org.gradle.internal.resources.DefaultResourceLockCoordinationService;
import org.gradle.internal.resources.ResourceLockCoordinationService;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.ServiceRegistryBuilder;
import org.gradle.internal.session.CrossBuildSessionState;
import org.gradle.internal.service.scopes.GradleUserHomeScopeServiceRegistry;
import org.gradle.internal.service.scopes.ServiceRegistryFactory;
import org.gradle.internal.session.BuildSessionState;
import org.gradle.internal.time.Time;
import org.gradle.internal.work.WorkerLeaseService;
import org.gradle.invocation.DefaultGradle;
import org.gradle.util.Path;

import java.io.File;
import java.util.Collections;

public class ProjectBuilderImpl {
    private static ServiceRegistry globalServices;

    public Project createChildProject(String name, Project parent, File projectDir) {
        ProjectInternal parentProject = (ProjectInternal) parent;
        ProjectDescriptorRegistry descriptorRegistry = parentProject.getServices().get(ProjectDescriptorRegistry.class);
        DefaultProjectDescriptor parentDescriptor = descriptorRegistry.getProject(parentProject.getPath());

        projectDir = (projectDir != null) ? projectDir.getAbsoluteFile() : new File(parentProject.getProjectDir(), name);
        DefaultProjectDescriptor projectDescriptor = new DefaultProjectDescriptor(parentDescriptor, name, projectDir, descriptorRegistry, parentProject.getServices().get(FileResolver.class));
        descriptorRegistry.addProject(projectDescriptor);

        parentProject.getServices().get(ProjectStateRegistry.class).registerProject(parentProject.getServices().get(BuildState.class), projectDescriptor);
        ProjectInternal project = parentProject.getServices().get(IProjectFactory.class).createProject(parentProject.getGradle(), projectDescriptor, parentProject, parentProject.getClassLoaderScope().createChild("project-" + name), parentProject.getBaseClassLoaderScope());

        // Lock the project, these won't ever be released as ProjectBuilder has no lifecycle
        ResourceLockCoordinationService coordinationService = project.getServices().get(ResourceLockCoordinationService.class);
        coordinationService.withStateLock(DefaultResourceLockCoordinationService.lock(project.getMutationState().getAccessLock()));

        return project;
    }

    public Project createProject(String name, File inputProjectDir, File gradleUserHomeDir) {
        File projectDir = prepareProjectDir(inputProjectDir);

        final File homeDir = new File(projectDir, "gradleHome");

        StartParameter startParameter = new StartParameterInternal();

        File userHomeDir = gradleUserHomeDir == null ? new File(projectDir, "userHome") : FileUtils.canonicalize(gradleUserHomeDir);
        startParameter.setGradleUserHomeDir(userHomeDir);
        NativeServices.initialize(userHomeDir);

        BuildRequestMetaData buildRequestMetaData = new DefaultBuildRequestMetaData(Time.currentTimeMillis());
        CrossBuildSessionState crossBuildSessionState = new CrossBuildSessionState(getGlobalServices(), startParameter);
        GradleUserHomeScopeServiceRegistry userHomeServices = getUserHomeServices();
        BuildSessionState buildSessionState = new BuildSessionState(userHomeServices, crossBuildSessionState, startParameter, buildRequestMetaData, ClassPath.EMPTY, new DefaultBuildCancellationToken(), buildRequestMetaData.getClient(), new NoOpBuildEventConsumer());
        BuildTreeState buildTreeState = new BuildTreeState(buildSessionState.getServices(), BuildType.TASKS);
        TestBuildScopeServices buildServices = new TestBuildScopeServices(buildTreeState.getServices(), homeDir);
        TestRootBuild build = new TestRootBuild(projectDir);
        buildServices.add(BuildState.class, build);

        buildServices.get(BuildStateRegistry.class).attachRootBuild(build);

        GradleInternal gradle = buildServices.get(InstantiatorFactory.class).decorateLenient().newInstance(DefaultGradle.class, null, startParameter, buildServices.get(ServiceRegistryFactory.class));
        gradle.setIncludedBuilds(Collections.emptyList());

        ProjectDescriptorRegistry projectDescriptorRegistry = buildServices.get(ProjectDescriptorRegistry.class);
        DefaultProjectDescriptor projectDescriptor = new DefaultProjectDescriptor(null, name, projectDir, projectDescriptorRegistry, buildServices.get(FileResolver.class));
        projectDescriptorRegistry.addProject(projectDescriptor);

        ClassLoaderScope baseScope = gradle.getClassLoaderScope();
        ClassLoaderScope rootProjectScope = baseScope.createChild("root-project");

        buildServices.get(ProjectStateRegistry.class).registerProject(build, projectDescriptor);
        ProjectInternal project = buildServices.get(IProjectFactory.class).createProject(gradle, projectDescriptor, null, rootProjectScope, baseScope);

        gradle.setRootProject(project);
        gradle.setDefaultProject(project);

        // Take a root worker lease and lock the project, these won't ever be released as ProjectBuilder has no lifecycle
        ResourceLockCoordinationService coordinationService = buildServices.get(ResourceLockCoordinationService.class);
        WorkerLeaseService workerLeaseService = buildServices.get(WorkerLeaseService.class);
        coordinationService.withStateLock(DefaultResourceLockCoordinationService.lock(workerLeaseService.getWorkerLease(), project.getMutationState().getAccessLock()));

        return project;
    }

    private GradleUserHomeScopeServiceRegistry getUserHomeServices() {
        ServiceRegistry globalServices = getGlobalServices();
        return globalServices.get(GradleUserHomeScopeServiceRegistry.class);
    }

    public synchronized static ServiceRegistry getGlobalServices() {
        if (globalServices == null) {
            globalServices = ServiceRegistryBuilder
                .builder()
                .displayName("global services")
                .parent(LoggingServiceRegistry.newNestedLogging())
                .parent(NativeServices.getInstance())
                .provider(new TestGlobalScopeServices())
                .build();
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

    public File prepareProjectDir(File projectDir) {
        if (projectDir == null) {
            TemporaryFileProvider temporaryFileProvider = new TmpDirTemporaryFileProvider();
            projectDir = temporaryFileProvider.createTemporaryDirectory("gradle", "projectDir");
            // TODO deleteOnExit won't clean up non-empty directories (and it leaks memory for long-running processes).
            projectDir.deleteOnExit();
        } else {
            projectDir = FileUtils.canonicalize(projectDir);
        }
        return projectDir;
    }

    private static class TestRootBuild extends AbstractBuildState implements RootBuildState {
        private final File rootProjectDir;

        public TestRootBuild(File rootProjectDir) {
            this.rootProjectDir = rootProjectDir;
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
        public SettingsInternal getLoadedSettings() {
            throw new UnsupportedOperationException();
        }

        @Override
        public NestedBuildFactory getNestedBuildFactory() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Path getCurrentPrefixForProjectsInChildBuilds() {
            return Path.ROOT;
        }

        @Override
        public Path getIdentityPathForProject(Path projectPath) {
            return projectPath;
        }

        @Override
        public StartParameter getStartParameter() {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T run(Transformer<T, ? super BuildController> buildAction) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ProjectComponentIdentifier getIdentifierForProject(Path projectPath) {
            String name = projectPath.getName();
            if (name == null) {
                name = "root";
            }
            return new DefaultProjectComponentIdentifier(getBuildIdentifier(), projectPath, projectPath, name);
        }

        @Override
        public File getBuildRootDir() {
            return rootProjectDir;
        }
    }
}
