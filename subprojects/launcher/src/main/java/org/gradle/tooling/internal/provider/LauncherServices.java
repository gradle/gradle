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

import org.gradle.cache.CacheRepository;
import org.gradle.initialization.GradleLauncherFactory;
import org.gradle.internal.classloader.ClassLoaderFactory;
import org.gradle.internal.composite.CompositeBuildActionParameters;
import org.gradle.internal.composite.CompositeBuildActionRunner;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.filewatch.FileWatcherFactory;
import org.gradle.internal.invocation.BuildActionRunner;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.scopes.PluginServiceRegistry;
import org.gradle.launcher.exec.*;
import org.gradle.internal.logging.text.StyledTextOutputFactory;

import java.util.List;

public class LauncherServices implements PluginServiceRegistry {
    public void registerGlobalServices(ServiceRegistration registration) {
        registration.addProvider(new ToolingGlobalScopeServices());
    }

    public void registerBuildSessionServices(ServiceRegistration registration) {
        registration.addProvider(new ToolingBuildSessionScopeServices());
    }

    public void registerBuildServices(ServiceRegistration registration) {

    }

    public void registerGradleServices(ServiceRegistration registration) {
    }

    public void registerProjectServices(ServiceRegistration registration) {
    }

    static class ToolingGlobalScopeServices {
        BuildExecuter createBuildExecuter(GradleLauncherFactory gradleLauncherFactory, ServiceRegistry globalServices, ListenerManager listenerManager, FileWatcherFactory fileWatcherFactory, ExecutorFactory executorFactory, StyledTextOutputFactory styledTextOutputFactory) {
            List<BuildActionRunner> buildActionRunners = globalServices.getAll(BuildActionRunner.class);
            BuildActionExecuter<BuildActionParameters> delegate = new InProcessBuildActionExecuter(gradleLauncherFactory, new ChainingBuildActionRunner(buildActionRunners));

            BuildActionExecuter<CompositeBuildActionParameters> compositeDelegate;
            List<CompositeBuildActionRunner> compositeBuildActionRunners = globalServices.getAll(CompositeBuildActionRunner.class);
            if (compositeBuildActionRunners.size() > 0) {
                compositeDelegate = new CompositeBuildActionExecuter(compositeBuildActionRunners.get(0));
            } else {
                compositeDelegate = null;
            }
            return new ContinuousBuildActionExecuter(delegate, fileWatcherFactory, listenerManager, styledTextOutputFactory, executorFactory, compositeDelegate);
        }

        ExecuteBuildActionRunner createExecuteBuildActionRunner() {
            return new ExecuteBuildActionRunner();
        }

        ClassLoaderCache createClassLoaderCache() {
            return new ClassLoaderCache();
        }

        JarCache createJarCache() {
            return new JarCache();
        }

    }

    static class ToolingBuildSessionScopeServices {
        PayloadClassLoaderFactory createClassLoaderFactory(ClassLoaderFactory classLoaderFactory, JarCache jarCache, CacheRepository cacheRepository) {
            return new DaemonSidePayloadClassLoaderFactory(
                new ModelClassLoaderFactory(
                    classLoaderFactory),
                jarCache,
                cacheRepository);
        }

        PayloadSerializer createPayloadSerializer(ClassLoaderCache classLoaderCache, PayloadClassLoaderFactory classLoaderFactory) {
            return new PayloadSerializer(
                new DefaultPayloadClassLoaderRegistry(
                    classLoaderCache,
                    classLoaderFactory)
            );
        }
    }
}
