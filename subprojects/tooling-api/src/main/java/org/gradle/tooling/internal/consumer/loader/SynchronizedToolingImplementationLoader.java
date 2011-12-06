package org.gradle.tooling.internal.consumer.loader;

import org.gradle.tooling.internal.consumer.Distribution;
import org.gradle.tooling.internal.protocol.ConnectionVersion4;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * by Szczepan Faber, created at: 12/6/11
 */
public class SynchronizedToolingImplementationLoader implements ToolingImplementationLoader {

    private final Lock lock = new ReentrantLock();
    private final ToolingImplementationLoader delegate;

    public SynchronizedToolingImplementationLoader(ToolingImplementationLoader delegate) {
        this.delegate = delegate;
    }

    public ConnectionVersion4 create(Distribution distribution) {
        lock.lock();
        try {
            return delegate.create(distribution);
        } finally {
            lock.unlock();
        }
    }
}