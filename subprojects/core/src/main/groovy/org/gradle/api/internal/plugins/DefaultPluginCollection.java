/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.internal.plugins;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.internal.DefaultDomainObjectContainer;
import org.gradle.api.plugins.PluginCollection;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.util.DeprecationLogger;

public class DefaultPluginCollection<T extends Plugin> extends DefaultDomainObjectContainer<T>
        implements PluginCollection<T> {
    public DefaultPluginCollection(Class<T> type) {
        super(type);
    }

    protected DefaultPluginCollection(Class<T> type, ObjectStore<T> store) {
        super(type, store);
    }

    public PluginCollection<T> matching(Spec<? super T> spec) {
        return new DefaultPluginCollection<T>(getType(), storeWithSpec(spec));
    }

    @Override
    public PluginCollection<T> matching(Closure spec) {
        return matching(Specs.convertClosureToSpec(spec));
    }

    public <S extends T> PluginCollection<S> withType(Class<S> type) {
        return new DefaultPluginCollection<S>(type, storeWithType(type));
    }

    public Action<? super T> whenPluginAdded(Action<? super T> action) {
        return whenObjectAdded(action);
    }

    public void whenPluginAdded(Closure closure) {
        whenObjectAdded(closure);
    }

    public void allPlugins(Action<? super T> action) {
        DeprecationLogger.nagUser("PluginCollection.allPlugins()", "all()");
        all(action);
    }

    public void allPlugins(Closure closure) {
        DeprecationLogger.nagUser("PluginCollection.allPlugins()", "all()");
        all(closure);
    }
}
