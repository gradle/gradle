/*
 * Copyright 2014 the original author or authors.
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

import com.google.common.collect.MapMaker;
import net.jcip.annotations.ThreadSafe;
import org.gradle.api.Transformer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@ThreadSafe
public class ClassLoaderCache {
    private final Lock lock = new ReentrantLock();
    private final Map<ClassLoader, ClassLoaderDetails> classLoaderDetails;
    private final Map<UUID, ClassLoader> classLoaderIds;

    public ClassLoaderCache() {
        classLoaderDetails = new MapMaker().weakKeys().makeMap();
        classLoaderIds = new MapMaker().softValues().makeMap();
    }

    public ClassLoader getClassLoader(ClassLoaderDetails details, Transformer<ClassLoader, ClassLoaderDetails> factory) {
        lock.lock();
        try {
            ClassLoader classLoader = classLoaderIds.get(details.uuid);
            if (classLoader != null) {
                return classLoader;
            }

            classLoader = factory.transform(details);
            classLoaderIds.put(details.uuid, classLoader);
            classLoaderDetails.put(classLoader, details);
            return classLoader;
        } finally {
            lock.unlock();
        }
    }

    public ClassLoaderDetails getDetails(ClassLoader classLoader, Transformer<ClassLoaderDetails, ClassLoader> factory) {
        lock.lock();
        try {
            ClassLoaderDetails details = classLoaderDetails.get(classLoader);
            if (details != null) {
                return details;
            }

            details = factory.transform(classLoader);
            classLoaderDetails.put(classLoader, details);
            classLoaderIds.put(details.uuid, classLoader);
            return details;
        } finally {
            lock.unlock();
        }
    }
}
