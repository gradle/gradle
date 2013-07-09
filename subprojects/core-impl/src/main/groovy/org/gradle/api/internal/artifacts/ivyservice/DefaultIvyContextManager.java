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
import org.gradle.api.internal.Transformers;

import java.util.LinkedList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class DefaultIvyContextManager implements IvyContextManager {
    private static final int MAX_CACHED_IVY_INSTANCES = 4;
    private final Lock lock = new ReentrantLock();
    private boolean messageAdapterAttached;
    private final LinkedList<Ivy> cached = new LinkedList<Ivy>();

    public void withIvy(final Action<? super Ivy> action) {
        withIvy(Transformers.toTransformer(action));
    }

    public <T> T withIvy(Transformer<? extends T, ? super Ivy> action) {
        IvyContext.pushNewContext();
        try {
            Ivy ivy = getIvy();
            try {
                IvyContext.getContext().setIvy(ivy);
                return action.transform(ivy);
            } finally {
                releaseIvy(ivy);
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
        return Ivy.newInstance(new IvySettings());
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
