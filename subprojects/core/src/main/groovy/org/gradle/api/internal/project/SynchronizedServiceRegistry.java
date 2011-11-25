/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.api.internal.project;

import org.gradle.api.internal.Synchronizer;
import org.gradle.api.internal.Factory;

/**
 * TODO SF - along with other Services related stuff and Synchronizer,
 * move to a better package, internal.service for instance
 *
 * by Szczepan Faber, created at: 11/24/11
 */
public class SynchronizedServiceRegistry implements ServiceRegistry {
    private final Synchronizer synchronizer = new Synchronizer();
    private final ServiceRegistry delegate;

    public SynchronizedServiceRegistry(ServiceRegistry delegate) {
        this.delegate = delegate;
    }

    public <T> T get(final Class<T> serviceType) throws UnknownServiceException {
        return synchronizer.synchronize(new Factory<T>() {
            public T create() {
                return delegate.get(serviceType);
            }
        });
    }

    public <T> Factory<T> getFactory(final Class<T> type) throws UnknownServiceException {
        return synchronizer.synchronize(new Factory<Factory<T>>() {
            public Factory<T> create() {
                return delegate.getFactory(type);
            }
        });
    }

    public <T> T newInstance(final Class<T> type) throws UnknownServiceException {
        return synchronizer.synchronize(new Factory<T>() {
            public T create() {
                return delegate.newInstance(type);
            }
        });
    }
}
