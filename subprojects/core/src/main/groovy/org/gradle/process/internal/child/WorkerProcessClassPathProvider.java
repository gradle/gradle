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

import org.gradle.api.Action;
import org.gradle.api.internal.AbstractClassPathProvider;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.PersistentCache;
import org.gradle.process.internal.launcher.BootstrapClassLoaderWorker;
import org.gradle.process.internal.launcher.GradleWorkerMain;
import org.gradle.util.GFileUtils;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

public class WorkerProcessClassPathProvider extends AbstractClassPathProvider {
    private final CacheRepository cacheRepository;
    private final Object lock = new Object();
    private Set<File> workerClassPath;

    public WorkerProcessClassPathProvider(CacheRepository cacheRepository) {
        this.cacheRepository = cacheRepository;
        add("WORKER_PROCESS", toPatterns("gradle-core", "gradle-cli", "slf4j-api", "logback-classic", "logback-core", "jul-to-slf4j"));
    }

    public Set<File> findClassPath(String name) {
        if (!name.equals("WORKER_MAIN")) {
            return super.findClassPath(name);
        }

        synchronized (lock) {
            if (workerClassPath == null) {
                PersistentCache cache = cacheRepository.cache("workerMain").withInitializer(new CacheInitializer()).open();
                workerClassPath = Collections.singleton(classesDir(cache));
            }
            return workerClassPath;
        }
    }

    private static File classesDir(PersistentCache cache) {
        return new File(cache.getBaseDir(), "classes");
    }

    private static class CacheInitializer implements Action<PersistentCache> {
        public void execute(PersistentCache cache) {
            File classesDir = classesDir(cache);
            for (Class<?> aClass : Arrays.asList(GradleWorkerMain.class, BootstrapClassLoaderWorker.class)) {
                String fileName = aClass.getName().replace('.', '/') + ".class";
                GFileUtils.copyURLToFile(WorkerProcessClassPathProvider.class.getClassLoader().getResource(fileName),
                        new File(classesDir, fileName));
            }
        }
    }

}
