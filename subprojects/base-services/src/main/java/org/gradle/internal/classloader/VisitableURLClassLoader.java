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
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class VisitableURLClassLoader extends URLClassLoader implements ClassLoaderHierarchy {
    static {
        try {
            //noinspection Since15
            ClassLoader.registerAsParallelCapable();
        } catch (NoSuchMethodError ignore) {
            // Not supported on Java 6
        }
    }

    public VisitableURLClassLoader(ClassLoader parent, Collection<URL> urls) {
        super(urls.toArray(new URL[0]), parent);
    }

    public VisitableURLClassLoader(ClassLoader parent, ClassPath classPath) {
        super(classPath.getAsURLArray(), parent);
    }

    public void visit(ClassLoaderVisitor visitor) {
        URL[] urls = getURLs();
        visitor.visitSpec(new Spec(Arrays.asList(urls)));
        visitor.visitClassPath(urls);
        visitor.visitParent(getParent());
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
            return "{url-class-loader " + " classpath:" + classpath + "}";
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
