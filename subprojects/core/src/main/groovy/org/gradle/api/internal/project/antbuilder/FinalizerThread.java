package org.gradle.api.internal.project.antbuilder;

import groovy.transform.CompileStatic;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.util.Map;

@CompileStatic
public class FinalizerThread extends Thread {
    private final ReferenceQueue<String> referenceQueue;
    private final Map<SoftReference<String>, ClassPathToClassLoader> classLoaderCache;
    private boolean stopped;

    public FinalizerThread(Map<SoftReference<String>, ClassPathToClassLoader> classLoaderCache) {
        this.setName("Classloader cache reference queue poller");
        this.setDaemon(true);
        this.classLoaderCache = classLoaderCache;
        this.referenceQueue = new ReferenceQueue<String>();
    }

    public void run() {
        try {
            while (!stopped) {
                Reference<? extends String> key = referenceQueue.remove();
                ClassPathToClassLoader cached = classLoaderCache.remove(key);
                cached.cleanup();
            }
        } catch (InterruptedException e) {
            // noop
        }
    }

    public SoftReference<String> referenceOf(String str) {
        return new SoftReference<String>(str, referenceQueue);
    }

    public void exit() {
        stopped = true;
        interrupt();
    }

}
