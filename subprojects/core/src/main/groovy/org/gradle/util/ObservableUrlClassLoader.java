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

package org.gradle.util;

import org.gradle.api.Action;
import org.gradle.listener.ActionBroadcast;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collection;

public class ObservableUrlClassLoader extends URLClassLoader {
    private final ActionBroadcast<ObservableUrlClassLoader> broadcast = new ActionBroadcast<ObservableUrlClassLoader>();

    public ObservableUrlClassLoader(ClassLoader parent, URL... urls) {
        super(urls, parent);
    }

    public ObservableUrlClassLoader(ClassLoader parent, Collection<URL> urls) {
        super(urls.toArray(new URL[urls.size()]), parent);
    }

    public void whenUrlAdded(Action<? super ObservableUrlClassLoader> action) {
        broadcast.add(action);
    }

    @Override
    public void addURL(URL url) {
        super.addURL(url);
        broadcast.execute(this);
    }

    public void addURLs(Iterable<URL> urls) {
        for (URL url : urls) {
            addURL(url);
        }
    }
}
