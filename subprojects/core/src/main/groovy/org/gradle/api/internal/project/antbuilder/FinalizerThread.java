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
