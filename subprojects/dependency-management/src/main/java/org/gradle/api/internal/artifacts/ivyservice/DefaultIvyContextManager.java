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

package org.gradle.api.internal.artifacts.ivyservice;

import org.apache.ivy.Ivy;
import org.apache.ivy.core.IvyContext;
import org.apache.ivy.core.settings.IvySettings;
import org.apache.ivy.util.Message;
import org.gradle.api.Action;
import org.gradle.api.Transformer;
import org.gradle.internal.Factory;
import org.gradle.internal.SystemProperties;
import org.gradle.internal.Transformers;

import java.util.LinkedList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class DefaultIvyContextManager implements IvyContextManager {
    private static final int MAX_CACHED_IVY_INSTANCES = 4;
    private final Lock lock = new ReentrantLock();
    private boolean messageAdapterAttached;
    private final LinkedList<Ivy> cached = new LinkedList<Ivy>();
    private final ThreadLocal<Integer> depth = new ThreadLocal<Integer>();

    @Override
    public void withIvy(final Action<? super Ivy> action) {
        withIvy(Transformers.toTransformer(action));
    }

    @Override
    public <T> T withIvy(Transformer<? extends T, ? super Ivy> action) {
        Integer currentDepth = depth.get();

        if (currentDepth != null) {
            depth.set(currentDepth + 1);
            try {
                return action.transform(IvyContext.getContext().getIvy());
            } finally {
                depth.set(currentDepth);
            }
        }

        IvyContext.pushNewContext();
        try {
            depth.set(1);
            try {
                Ivy ivy = getIvy();
                try {
                    IvyContext.getContext().setIvy(ivy);
                    return action.transform(ivy);
                } finally {
                    releaseIvy(ivy);
                }
            } finally {
                depth.set(null);
            }
        } finally {
            IvyContext.popContext();
        }
    }

    private Ivy getIvy() {
        lock.lock();
        try {
            if (!cached.isEmpty()) {
                return cached.removeFirst();
            }
            if (!messageAdapterAttached) {
                Message.setDefaultLogger(new IvyLoggingAdaper());
                messageAdapterAttached = true;
            }
        } finally {
            lock.unlock();
        }
        return createNewIvyInstance();
    }

    /*
     * Synchronizes on the system properties, because IvySettings iterates
     * over them without taking a defensive copy. This can fail if another
     * process sets a system property at that moment.
     */
    private Ivy createNewIvyInstance() {
        return SystemProperties.getInstance().withSystemProperties(new Factory<Ivy>() {
            @Override
            public Ivy create() {
                return Ivy.newInstance(new IvySettings());
            }
        });
    }

    private void releaseIvy(Ivy ivy) {
        // cleanup
        ivy.getSettings().getResolvers().clear();
        ivy.getSettings().setDefaultResolver(null);

        lock.lock();
        try {
            if (cached.size() < MAX_CACHED_IVY_INSTANCES) {
                cached.add(ivy);
            }
            // else, throw it away
        } finally {
            lock.unlock();
        }
    }
}
