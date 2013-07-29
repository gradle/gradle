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

package org.gradle.internal.classloader;

import org.gradle.internal.classpath.ClassPath;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collection;

public class MutableURLClassLoader extends URLClassLoader {
    public MutableURLClassLoader(ClassLoader parent, URL... urls) {
        super(urls, parent);
    }

    public MutableURLClassLoader(ClassLoader parent, Collection<URL> urls) {
        super(urls.toArray(new URL[urls.size()]), parent);
    }

    public MutableURLClassLoader(ClassLoader parent, ClassPath classPath) {
        super(classPath.getAsURLArray(), parent);
    }

    @Override
    public void addURL(URL url) {
        super.addURL(url);
    }

    public void addURLs(Iterable<URL> urls) {
        for (URL url : urls) {
            addURL(url);
        }
    }
}
