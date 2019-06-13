/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.initialization;

import com.google.common.collect.Sets;
import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.internal.classloader.ClasspathUtil;
import org.gradle.internal.classloader.FilteringClassLoader;
import org.gradle.internal.classloader.VisitableURLClassLoader;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.internal.reflect.Instantiator;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Set;

public class DefaultClassLoaderRegistry implements ClassLoaderRegistry {
    private final ClassLoader apiOnlyClassLoader;
    private final ClassLoader apiAndPluginsClassLoader;
    private final ClassLoader pluginsClassLoader;
    private final FilteringClassLoader.Spec gradleApiSpec;
    private final MixInLegacyTypesClassLoader.Spec workerExtensionSpec;
    private final Instantiator instantiator;

    public DefaultClassLoaderRegistry(ClassPathRegistry classPathRegistry, LegacyTypesSupport legacyTypesSupport, Instantiator instantiator) {
        this.instantiator = instantiator;
        ClassLoader runtimeClassLoader = getClass().getClassLoader();
        this.apiOnlyClassLoader = restrictToGradleApi(runtimeClassLoader);
        this.pluginsClassLoader = new MixInLegacyTypesClassLoader(runtimeClassLoader, classPathRegistry.getClassPath("GRADLE_EXTENSIONS"), legacyTypesSupport);
        this.gradleApiSpec = apiSpecFor(pluginsClassLoader);
        this.workerExtensionSpec = new MixInLegacyTypesClassLoader.Spec("legacy-mixin-loader", classPathRegistry.getClassPath("GRADLE_WORKER_EXTENSIONS").getAsURLs());
        this.apiAndPluginsClassLoader = restrictTo(gradleApiSpec, pluginsClassLoader);
    }

    private ClassLoader restrictToGradleApi(ClassLoader classLoader) {
        return restrictTo(apiSpecFor(classLoader), classLoader);
    }

    private static ClassLoader restrictTo(FilteringClassLoader.Spec spec, ClassLoader parent) {
        return new FilteringClassLoader(parent, spec);
    }

    private FilteringClassLoader.Spec apiSpecFor(ClassLoader classLoader) {
        FilteringClassLoader.Spec apiSpec = new FilteringClassLoader.Spec();
        GradleApiSpecProvider.Spec apiAggregate = new GradleApiSpecAggregator(classLoader, instantiator).aggregate();
        for (String resource : apiAggregate.getExportedResources()) {
            apiSpec.allowResource(resource);
        }
        for (String resourcePrefix : apiAggregate.getExportedResourcePrefixes()) {
            apiSpec.allowResources(resourcePrefix);
        }
        for (Class<?> clazz : apiAggregate.getExportedClasses()) {
            apiSpec.allowClass(clazz);
        }
        for (String packageName : apiAggregate.getExportedPackages()) {
            apiSpec.allowPackage(packageName);
        }
        return apiSpec;
    }

    @Override
    public ClassLoader getRuntimeClassLoader() {
        return getClass().getClassLoader();
    }

    @Override
    public ClassLoader getGradleApiClassLoader() {
        return apiAndPluginsClassLoader;
    }

    @Override
    public ClassLoader getPluginsClassLoader() {
        return pluginsClassLoader;
    }

    @Override
    public ClassLoader getGradleCoreApiClassLoader() {
        return apiOnlyClassLoader;
    }

    @Override
    public FilteringClassLoader.Spec getGradleApiFilterSpec() {
        return new FilteringClassLoader.Spec(gradleApiSpec);
    }

    @Override
    public MixInLegacyTypesClassLoader.Spec getGradleWorkerExtensionSpec() {
        return workerExtensionSpec;
    }

    @Override
    public VisitableURLClassLoader.Spec getUserSpec(String name, Iterable<File> additionalClasspath, Class<?>... classes) {
        Set<URL> classpath = Sets.newLinkedHashSet();
        classpath.addAll(DefaultClassPath.of(additionalClasspath).getAsURLs());

        Set<ClassLoader> uniqueClassloaders = Sets.newHashSet();
        for (Class clazz : classes) {
            ClassLoader classLoader = clazz.getClassLoader();
            // System types come from the system classloader and their classloader is null.
            if (classLoader != null) {
                uniqueClassloaders.add(classLoader);
            }
        }
        for (ClassLoader classLoader : uniqueClassloaders) {
            ClasspathUtil.getClasspath(classLoader, classpath, getGradleApiClassLoader());
        }
        return new VisitableURLClassLoader.Spec(name, new ArrayList<>(classpath));
    }
}
