/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.initialization;

import org.gradle.internal.event.AnonymousListenerBroadcast;
import org.gradle.internal.event.ListenerBroadcast;

/**
 * This is a work around for event scoping. It would be simpler to fire these events at the global scope and subscribe to them in child scopes.
 * However, currently events only travel up scopes and cannot be observed from child scopes. This class simulates being able to receive events
 * from child scopes.
 */
public class ClassLoaderScopeListeners {
    private ListenerBroadcast<ClassLoaderScopeRegistryListener> broadcast = new AnonymousListenerBroadcast<>(ClassLoaderScopeRegistryListener.class);

    public ClassLoaderScopeRegistryListener getBroadcast() {
        return broadcast.getSource();
    }

    public void addListener(ClassLoaderScopeRegistryListener listener) {
        broadcast.add(listener);
    }

    public void removeListener(ClassLoaderScopeRegistryListener listener) {
        broadcast.remove(listener);
    }
}
