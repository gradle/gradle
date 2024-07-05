/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.service;

class LazyServiceWrapper implements Service {

    private final Service delegate;

    public LazyServiceWrapper(Service delegate) {
        this.delegate = delegate;
    }

    @Override
    public String getDisplayName() {
        return "LazyService(" + delegate.getDisplayName() + ")";
    }

    @Override
    public Object get() {
        return asLazyService();
    }

    @Override
    public <T> LazyService<T> asLazyService() {
        return delegate.asLazyService();
    }

    @Override
    public void requiredBy(ServiceProvider serviceProvider) {
        delegate.requiredBy(serviceProvider);
    }

    @Override
    public String toString() {
        return getDisplayName();
    }
}
