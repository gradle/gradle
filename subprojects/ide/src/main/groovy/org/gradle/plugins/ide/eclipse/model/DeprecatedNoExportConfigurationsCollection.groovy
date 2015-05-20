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

package org.gradle.plugins.ide.eclipse.model

import org.gradle.api.artifacts.Configuration
import org.gradle.util.DeprecationLogger

class DeprecatedNoExportConfigurationsCollection implements Collection<Configuration> {
    public static final String DEPRECATED_NOEXPORTCONFIGURATION = "EclipseClasspath.noExportConfigurations"
    private Collection delegate

    DeprecatedNoExportConfigurationsCollection(Collection<Configuration> delegate) {
        this.delegate = delegate
    }

    @Override
    int size() {
        return delegate.size()
    }

    @Override
    boolean isEmpty() {
        return delegate.isEmpty()
    }

    @Override
    boolean contains(Object o) {
        return delegate.contains(o)
    }

    @Override
    Iterator<Configuration> iterator() {
        return delegate.iterator()
    }

    @Override
    Object[] toArray() {
        return delegate.toArray()
    }

    @Override
    def <T> T[] toArray(T[] a) {
        return delegate.toArray(a)
    }

    @Override
    boolean add(Configuration files) {
        DeprecationLogger.nagUserOfDeprecated(DEPRECATED_NOEXPORTCONFIGURATION)

        return delegate.add(files)
    }

    @Override
    boolean remove(Object o) {
        DeprecationLogger.nagUserOfDeprecated(DEPRECATED_NOEXPORTCONFIGURATION)
        return delegate.remove(o)
    }

    @Override
    boolean containsAll(Collection<?> c) {
        return delegate.containsAll(c)
    }

    @Override
    boolean addAll(Collection<? extends Configuration> c) {
        DeprecationLogger.nagUserOfDeprecated(DEPRECATED_NOEXPORTCONFIGURATION)

        return delegate.addAll(c)
    }

    @Override
    boolean removeAll(Collection<?> c) {
        DeprecationLogger.nagUserOfDeprecated(DEPRECATED_NOEXPORTCONFIGURATION)

        return delegate.addAll(c)
    }

    @Override
    boolean retainAll(Collection<?> c) {
        DeprecationLogger.nagUserOfDeprecated(DEPRECATED_NOEXPORTCONFIGURATION)
        return delegate.retainAll(c)
    }

    @Override
    void clear() {
        DeprecationLogger.nagUserOfDeprecated(DEPRECATED_NOEXPORTCONFIGURATION)
        delegate.clear()
    }
}
