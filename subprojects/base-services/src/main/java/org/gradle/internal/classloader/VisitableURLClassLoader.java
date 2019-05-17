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

import org.gradle.internal.Cast;
import org.gradle.internal.Factory;
import org.gradle.internal.classpath.ClassPath;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;

public class VisitableURLClassLoader extends URLClassLoader implements ClassLoaderHierarchy {
    static {
        try {
            ClassLoader.registerAsParallelCapable();
        } catch (NoSuchMethodError ignore) {
            // Not supported on Java 6
        }
    }

    private final EnumMap<UserData, Object> userData = new EnumMap<UserData, Object>(UserData.class);

    public enum UserData {
        NAMED_OBJECT_INSTANTIATOR
    }

    /**
     * This method can be used to store user data that should live among with this classloader
     * @param user the consumer
     * @param onMiss called to create the initial data, when not found
     * @param <T> the type of data
     * @return user data
     */
    public synchronized <T> T getUserData(UserData user, Factory<T> onMiss) {
        if (userData.containsKey(user)) {
            return Cast.uncheckedCast(userData.get(user));
        }
        T value = onMiss.create();
        userData.put(user, value);
        return value;
    }

    // TODO:lptr When we drop Java 8 support we can switch to using ClassLoader.getName() instead of storing our own
    private final String name;

    public VisitableURLClassLoader(String name, ClassLoader parent, Collection<URL> urls) {
        this(name, urls.toArray(new URL[0]), parent);
    }

    public VisitableURLClassLoader(String name, ClassLoader parent, ClassPath classPath) {
        this(name, classPath.getAsURLArray(), parent);
    }

    private VisitableURLClassLoader(String name, URL[] classpath, ClassLoader parent) {
        super(classpath, parent);
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @Override
    public void addURL(URL url) {
        super.addURL(url);
    }

    @Override
    public String toString() {
        return VisitableURLClassLoader.class.getSimpleName() + "(" + name + ")";
    }

    @Override
    public void visit(ClassLoaderVisitor visitor) {
        URL[] urls = getURLs();
        visitor.visitSpec(new Spec(name, Arrays.asList(urls)));
        visitor.visitClassPath(urls);
        visitor.visitParent(getParent());
    }

    public static class Spec extends ClassLoaderSpec {
        final String name;
        final List<URL> classpath;

        public String getName() {
            return name;
        }

        public Spec(String name, List<URL> classpath) {
            this.name = name;
            this.classpath = classpath;
        }

        public List<URL> getClasspath() {
            return classpath;
        }

        @Override
        public String toString() {
            return "{url-class-loader name:" + name + ", classpath:" + classpath + "}";
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
