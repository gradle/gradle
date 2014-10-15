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
import org.gradle.util.CollectionUtils;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collection;
import java.util.List;

public class MutableURLClassLoader extends URLClassLoader implements ClassLoaderHierarchy {
    public MutableURLClassLoader(ClassLoader parent, URL... urls) {
        super(urls, parent);
    }

    public MutableURLClassLoader(ClassLoader parent, Collection<URL> urls) {
        super(urls.toArray(new URL[urls.size()]), parent);
    }

    public MutableURLClassLoader(ClassLoader parent, ClassPath classPath) {
        super(classPath.getAsURLArray(), parent);
    }

    public MutableURLClassLoader(ClassLoader parent, Spec spec) {
        this(parent, spec.classpath);
    }

    public void visit(ClassLoaderVisitor visitor) {
        visitor.visitSpec(new Spec(CollectionUtils.toList(getURLs())));
        visitor.visitClassPath(getURLs());
        visitor.visitParent(getParent());
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

    public static class Spec extends ClassLoaderSpec {
        final List<URL> classpath;

        public Spec(List<URL> classpath) {
            this.classpath = classpath;
        }

        public List<URL> getClasspath() {
            return classpath;
        }

        @Override
        public String toString() {
            return String.format("[%s classpath:%s]", getClass().getSimpleName(), classpath);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != getClass()) {
                return false;
            }
            Spec other = (Spec) obj;
            return classpath.equals(other.classpath);
        }

        @Override
        public int hashCode() {
            return classpath.hashCode();
        }
    }
}
