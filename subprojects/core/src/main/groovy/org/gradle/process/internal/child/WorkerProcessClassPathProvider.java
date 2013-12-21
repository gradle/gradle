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

package org.gradle.process.internal.child;

import com.tonicsystems.jarjar.JarJarTask;
import com.tonicsystems.jarjar.Rule;
import org.apache.tools.ant.types.Resource;
import org.apache.tools.ant.types.ResourceCollection;
import org.apache.tools.ant.types.resources.URLResource;
import org.gradle.api.Action;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.ClassPathProvider;
import org.gradle.api.internal.classpath.ModuleRegistry;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.PersistentCache;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.process.internal.launcher.BootstrapClassLoaderWorker;
import org.gradle.process.internal.launcher.GradleWorkerMain;
import org.gradle.util.AntUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;

public class WorkerProcessClassPathProvider implements ClassPathProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(WorkerProcessClassPathProvider.class);
    private final CacheRepository cacheRepository;
    private final ModuleRegistry moduleRegistry;
    private final Object lock = new Object();
    private ClassPath workerClassPath;
    private PersistentCache workerClassPathCache;

    public WorkerProcessClassPathProvider(CacheRepository cacheRepository, ModuleRegistry moduleRegistry) {
        this.cacheRepository = cacheRepository;
        this.moduleRegistry = moduleRegistry;
    }

    public ClassPath findClassPath(String name) {
        if (name.equals("WORKER_PROCESS")) {
            // TODO - split out a logging project and use its classpath, instead of hardcoding logging dependencies here
            ClassPath classpath = new DefaultClassPath();
            classpath = classpath.plus(moduleRegistry.getModule("gradle-base-services").getImplementationClasspath());
            classpath = classpath.plus(moduleRegistry.getModule("gradle-core").getImplementationClasspath());
            classpath = classpath.plus(moduleRegistry.getModule("gradle-cli").getImplementationClasspath());
            classpath = classpath.plus(moduleRegistry.getModule("gradle-native").getImplementationClasspath());
            classpath = classpath.plus(moduleRegistry.getModule("gradle-messaging").getImplementationClasspath());
            classpath = classpath.plus(moduleRegistry.getExternalModule("slf4j-api").getClasspath());
            classpath = classpath.plus(moduleRegistry.getExternalModule("logback-classic").getClasspath());
            classpath = classpath.plus(moduleRegistry.getExternalModule("logback-core").getClasspath());
            classpath = classpath.plus(moduleRegistry.getExternalModule("jul-to-slf4j").getClasspath());
            classpath = classpath.plus(moduleRegistry.getExternalModule("guava-jdk5").getClasspath());
            return classpath;
        }
        if (name.equals("WORKER_MAIN")) {
            synchronized (lock) {
                if (workerClassPath == null) {
                    workerClassPathCache = cacheRepository
                            .cache("workerMain")
                            .withInitializer(new CacheInitializer())
                            .open();
                    workerClassPath = new DefaultClassPath(jarFile(workerClassPathCache));
                }
                LOGGER.debug("Using worker process classpath: {}", workerClassPath);
                return workerClassPath;
            }
        }

        return null;
    }

    public void close() {
        // This isn't quite right. Should close the worker classpath cache once we're finished with the worker processes. This may be before the end of this build
        // or they may be used across multiple builds
        synchronized (lock) {
            try {
                if (workerClassPathCache != null) {
                    workerClassPathCache.close();
                }
            } finally {
                workerClassPathCache = null;
                workerClassPath = null;
            }
        }
    }

    private static File jarFile(PersistentCache cache) {
        return new File(cache.getBaseDir(), "gradle-worker.jar");
    }

    private static class CacheInitializer implements Action<PersistentCache> {
        public void execute(PersistentCache cache) {
            File jarFile = jarFile(cache);
            LOGGER.debug("Generating worker process classes to {}.", jarFile);

            URL currentClasspath = getClass().getProtectionDomain().getCodeSource().getLocation();
            JarJarTask task = new JarJarTask();
            task.setDestFile(jarFile);

            final List<Resource> classResources = new ArrayList<Resource>();
            List<Class<?>> renamedClasses = Arrays.asList(GradleWorkerMain.class,
                    BootstrapSecurityManager.class,
                    EncodedStream.EncodedInput.class);
            List<Class<?>> classes = new ArrayList<Class<?>>();
            classes.add(BootstrapClassLoaderWorker.class);
            classes.addAll(renamedClasses);
            for (Class<?> aClass : classes) {
                final String fileName = aClass.getName().replace('.', '/') + ".class";

                // Prefer the class from the same classpath as the current class. This is for the case where we're running in a test under an older
                // version of Gradle, whose worker classes will be visible to us.
                // TODO - remove this once we have upgraded to a wrapper with these changes in it
                Enumeration<URL> resources;
                try {
                    resources = WorkerProcessClassPathProvider.class.getClassLoader().getResources(fileName);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                URL resource = null;
                while (resources.hasMoreElements()) {
                    URL url = resources.nextElement();
                    resource = url;
                    if (url.toString().startsWith(currentClasspath.toString())) {
                        break;
                    }
                }
                URLResource urlResource = new URLResource(resource) {
                    @Override
                    public synchronized String getName() {
                        return fileName;
                    }
                };
                classResources.add(urlResource);
            }

            task.add(new ResourceCollection() {
                public Iterator iterator() {
                    return classResources.iterator();
                }

                public int size() {
                    return classResources.size();
                }

                public boolean isFilesystemOnly() {
                    return true;
                }
            });

            for (Class<?> renamedClass : renamedClasses) {
                Rule rule = new Rule();
                rule.setPattern(renamedClass.getName());
                rule.setResult("jarjar.@0");
                task.addConfiguredRule(rule);
            }

            AntUtil.execute(task);
        }
    }

}
