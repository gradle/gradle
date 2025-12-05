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

import org.gradle.api.internal.classpath.DefaultModuleRegistry;
import org.gradle.api.internal.classpath.EffectiveClassPath;
import org.gradle.api.internal.classpath.Module;
import org.gradle.api.internal.classpath.ModuleRegistry;
import org.gradle.api.internal.classpath.UnknownModuleException;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.internal.CacheFactory;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.installation.CurrentGradleInstallation;
import org.gradle.internal.installation.GradleInstallation;
import org.gradle.internal.instrumentation.agent.AgentStatus;
import org.gradle.internal.logging.LoggingManagerFactory;
import org.gradle.internal.logging.LoggingManagerInternal;
import org.gradle.internal.operations.BuildOperationListenerManager;
import org.gradle.internal.operations.BuildOperationProgressEventEmitter;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.operations.CurrentBuildOperationRef;
import org.gradle.internal.operations.NoOpBuildOperationProgressEventEmitter;
import org.gradle.internal.service.Provides;
import org.gradle.internal.service.scopes.GlobalScopeServices;
import org.gradle.internal.time.Clock;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.Set;

public class TestGlobalScopeServices extends GlobalScopeServices {
    public TestGlobalScopeServices() {
        super(false, AgentStatus.disabled(), CurrentGradleInstallation.locate());
    }

    @Provides
    @Override
    protected CacheFactory createCacheFactory(FileLockManager fileLockManager, ExecutorFactory executorFactory, BuildOperationRunner buildOperationRunner) {
        return new TestInMemoryCacheFactory();
    }

    @Override
    @Provides
    protected ModuleRegistry createModuleRegistry(CurrentGradleInstallation currentGradleInstallation) {
        GradleInstallation installation = currentGradleInstallation.getInstallation();
        if (installation == null) {
            // This ProjectBuilder test is being executed from outside a Gradle distribution.
            ClassPath classpath = DefaultClassPath.of(new EffectiveClassPath(getClass().getClassLoader()).getAsFiles());
            return new MockModuleRegistry(classpath);
        } else {
            return new DefaultModuleRegistry(installation);
        }
    }

    @Provides
    LoggingManagerInternal createLoggingManager(LoggingManagerFactory loggingManagerFactory) {
        return loggingManagerFactory.createLoggingManager();
    }

    @Provides
    @Override
    protected BuildOperationProgressEventEmitter createBuildOperationProgressEventEmitter(
        Clock clock,
        CurrentBuildOperationRef currentBuildOperationRef,
        BuildOperationListenerManager listenerManager
    ) {
        return new NoOpBuildOperationProgressEventEmitter();
    }

    /**
     * A module registry backed by a classpath. Each module returned by this
     * registry has the same classpath, equal to the given backing classpath.
     * <p>
     * This is to be used in testing scenarios where it is assumed all classes necessary for Gradle
     * to function are present on the classpath already.
     */
    private static class MockModuleRegistry implements ModuleRegistry, Module {

        private final ClassPath classpath;

        public MockModuleRegistry(ClassPath classpath) {
            this.classpath = classpath;
        }

        @Override
        public Module getExternalModule(String name) throws UnknownModuleException {
            return this;
        }

        @Override
        public Module getModule(String name) throws UnknownModuleException {
            return this;
        }

        @Override
        public @Nullable Module findModule(String name) throws UnknownModuleException {
            return this;
        }

        @Override
        public ClassPath getImplementationClasspath() {
            return classpath;
        }

        @Override
        public ClassPath getRuntimeClasspath() {
            return classpath;
        }

        @Override
        public ClassPath getClasspath() {
            return classpath;
        }

        @Override
        public Set<Module> getRequiredModules() {
            return Collections.emptySet();
        }

        @Override
        public Set<Module> getAllRequiredModules() {
            return Collections.emptySet();
        }

        @Override
        public ClassPath getAllRequiredModulesClasspath() {
            return classpath;
        }

    }

}
