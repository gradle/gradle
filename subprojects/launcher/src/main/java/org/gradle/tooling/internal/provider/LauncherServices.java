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
import org.gradle.internal.invocation.BuildActionRunner;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.scopes.PluginServiceRegistry;
import org.gradle.launcher.exec.ChainingBuildActionRunner;
import org.gradle.launcher.exec.InProcessBuildActionExecuter;

import java.util.List;

public class LauncherServices implements PluginServiceRegistry {
    public void registerGlobalServices(ServiceRegistration registration) {
        registration.addProvider(new ToolingGlobalScopeServices());
    }

    public void registerBuildServices(ServiceRegistration registration) {
        registration.addProvider(new ToolingBuildScopeServices());
    }

    public void registerGradleServices(ServiceRegistration registration) {
    }

    public void registerProjectServices(ServiceRegistration registration) {
    }

    static class ToolingGlobalScopeServices {
        InProcessBuildActionExecuter createBuildActionExecuter(GradleLauncherFactory gradleLauncherFactory, ServiceRegistry services) {
            List<BuildActionRunner> buildActionRunners = services.getAll(BuildActionRunner.class);
            return new InProcessBuildActionExecuter(gradleLauncherFactory, new ChainingBuildActionRunner(buildActionRunners));
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

    static class ToolingBuildScopeServices {
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
