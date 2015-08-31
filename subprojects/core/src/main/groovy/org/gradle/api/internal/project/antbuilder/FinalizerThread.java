/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.api.internal.project.antbuilder;

import groovy.transform.CompileStatic;
import org.gradle.internal.classpath.ClassPath;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Map;

@CompileStatic
public class FinalizerThread extends Thread {
    private final ReferenceQueue<ClassPath> referenceQueue;
    private final Map<WeakReference<ClassPath>, ClassPathToClassLoader> classLoaderCache;
    private boolean stopped;

    public FinalizerThread(Map<WeakReference<ClassPath>, ClassPathToClassLoader> classLoaderCache) {
        this.setName("Classloader cache reference queue poller");
        this.setDaemon(true);
        this.classLoaderCache = classLoaderCache;
        this.referenceQueue = new ReferenceQueue<ClassPath>();
    }

    public void run() {
        try {
            while (!stopped) {
                Reference<? extends ClassPath> key = referenceQueue.remove();
                ClassPathToClassLoader cached = classLoaderCache.remove(key);
                cached.cleanup();
            }
        } catch (InterruptedException e) {
            // noop
        }
    }

    public WeakReference<ClassPath> referenceOf(ClassPath classPath) {
        return new WeakReference<ClassPath>(classPath, referenceQueue);
    }

    public void exit() {
        stopped = true;
        interrupt();
    }

}
