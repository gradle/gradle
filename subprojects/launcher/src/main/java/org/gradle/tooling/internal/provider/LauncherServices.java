/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.tooling.internal.provider;

import org.gradle.initialization.GradleLauncherFactory;
import org.gradle.internal.classpath.CachedClasspathTransformer;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.filewatch.FileWatcherFactory;
import org.gradle.internal.invocation.BuildActionRunner;
import org.gradle.internal.logging.text.StyledTextOutputFactory;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.scopes.GradleUserHomeScopePluginServices;
import org.gradle.internal.service.scopes.GradleUserHomeScopeServiceRegistry;
import org.gradle.internal.service.scopes.PluginServiceRegistry;
import org.gradle.launcher.exec.BuildExecuter;
import org.gradle.launcher.exec.ChainingBuildActionRunner;
import org.gradle.launcher.exec.InProcessBuildActionExecuter;
import org.gradle.tooling.internal.provider.serialization.ClassLoaderCache;
import org.gradle.tooling.internal.provider.serialization.DaemonSidePayloadClassLoaderFactory;
import org.gradle.tooling.internal.provider.serialization.DefaultPayloadClassLoaderRegistry;
import org.gradle.tooling.internal.provider.serialization.ModelClassLoaderFactory;
import org.gradle.tooling.internal.provider.serialization.PayloadClassLoaderFactory;
import org.gradle.tooling.internal.provider.serialization.PayloadSerializer;
import org.gradle.tooling.internal.provider.serialization.WellKnownClassLoaderRegistry;

import java.util.List;

public class LauncherServices implements PluginServiceRegistry, GradleUserHomeScopePluginServices {
    @Override
    public void registerGlobalServices(ServiceRegistration registration) {
        registration.addProvider(new ToolingGlobalScopeServices());
    }

    @Override
    public void registerGradleUserHomeServices(ServiceRegistration registration) {
        registration.addProvider(new ToolingBuildSessionScopeServices());
    }

    @Override
    public void registerBuildSessionServices(ServiceRegistration registration) {
    }

    @Override
    public void registerBuildServices(ServiceRegistration registration) {
    }

    @Override
    public void registerGradleServices(ServiceRegistration registration) {
    }

    @Override
    public void registerProjectServices(ServiceRegistration registration) {
    }

    static class ToolingGlobalScopeServices {
        BuildExecuter createBuildExecuter(GradleLauncherFactory gradleLauncherFactory, ServiceRegistry globalServices, ListenerManager listenerManager, FileWatcherFactory fileWatcherFactory, ExecutorFactory executorFactory, StyledTextOutputFactory styledTextOutputFactory, GradleUserHomeScopeServiceRegistry userHomeServiceRegistry) {
            List<BuildActionRunner> buildActionRunners = globalServices.getAll(BuildActionRunner.class);
            return new ServicesSetupBuildActionExecuter(
                new ContinuousBuildActionExecuter(
                    new InProcessBuildActionExecuter(gradleLauncherFactory,
                        new ChainingBuildActionRunner(buildActionRunners)),
                    fileWatcherFactory,
                    listenerManager,
                    styledTextOutputFactory,
                    executorFactory),
                userHomeServiceRegistry);
        }

        ExecuteBuildActionRunner createExecuteBuildActionRunner() {
            return new ExecuteBuildActionRunner();
        }

        ClassLoaderCache createClassLoaderCache() {
            return new ClassLoaderCache();
        }
    }

    static class ToolingBuildSessionScopeServices {
        PayloadClassLoaderFactory createClassLoaderFactory(CachedClasspathTransformer cachedClasspathTransformer) {
            return new DaemonSidePayloadClassLoaderFactory(
                new ModelClassLoaderFactory(),
                cachedClasspathTransformer);
        }

        PayloadSerializer createPayloadSerializer(ClassLoaderCache classLoaderCache, PayloadClassLoaderFactory classLoaderFactory) {
            return new PayloadSerializer(
                new WellKnownClassLoaderRegistry(
                    new DefaultPayloadClassLoaderRegistry(
                        classLoaderCache,
                        classLoaderFactory))
            );
        }
    }
}
