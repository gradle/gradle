/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.api.JavaVersion;
import org.gradle.util.internal.CollectionUtils;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;

import static java.lang.ClassLoader.getSystemClassLoader;
import static org.gradle.internal.UncheckedException.throwAsUncheckedException;

public class ClassLoaderVisitor {
    private static final String JAVA_CLASS_PATH = "java.class.path";
    private final @Nullable ClassLoader stopAt;

    public ClassLoaderVisitor() {
        this(getSystemClassLoader() == null ? null : getSystemClassLoader().getParent());
    }

    public ClassLoaderVisitor(@Nullable ClassLoader stopAt) {
        this.stopAt = stopAt;
    }

    public void visit(ClassLoader classLoader) {
        if (classLoader == stopAt) {
            visitSpec(SystemClassLoaderSpec.INSTANCE);
            return;
        }

        if (classLoader instanceof ClassLoaderHierarchy) {
            ((ClassLoaderHierarchy) classLoader).visit(this);
        } else {
            // In some cases, the classloader we are visiting may be a
            // ClassLoaderHierarchy, but the `ClassLoaderHierarchy` class is
            // loaded from a different classloader than the
            // ClassLoaderVisitor.class.getClassLoader(). In many cases as long
            // as the target classloader is a VisitableURLClassloader, we can still
            // interpret it as a URLClassLoader.
            if (classLoader instanceof URLClassLoader) {
                URL[] urls = ((URLClassLoader) classLoader).getURLs();
                visitOpaqueUrlClassloader(classLoader, urls);
            } else if (classLoader == ClassLoader.getSystemClassLoader()){
                // This branch is only reached in Java 9+, since the system classloader
                // implements URLClassLoader in Java <9.
                URL[] urls = extractJava9AppClasspath();
                visitOpaqueUrlClassloader(classLoader, urls);
            } else {
                // We've found a non-URLClassLoader classloader we do not recognize.
                // In some cases, this may be a FilteringClassLoader or other Gradle internal
                // classloader type, where the class implementing the classloader is loaded from
                // a different classloader than this current classloader, causing the
                // above `classLoader instanceof ClassLoaderHierarchy` to fail.
                // This can happen when running the daemon in-process from the TAPI, where the
                // daemon runs on-top of a FilteringClassLoader over the TAPI client classpath.

                // In this case, treat it as an empty classloader and continue visiting its parents.
                // TODO: This should eventually become an error. To resolve this, we should load the
                // actual daemon classpath when running the daemon in-process instead of trying to
                // run it on-top of the TAPI classpath.
                visitOpaqueUrlClassloader(classLoader, new URL[0]);
            }
        }
    }

    /**
     * Visit a classloader which can be interpreted as a VisitableURLClassLoader.
     */
    private void visitOpaqueUrlClassloader(ClassLoader classLoader, URL[] urls) {
        String name = getNameOf(classLoader);
        visitSpec(new VisitableURLClassLoader.Spec(name, CollectionUtils.toList(urls)));
        visitClassPath(urls);

        if (classLoader.getParent() != null) {
            visitParent(classLoader.getParent());
        }
    }

    @SuppressWarnings("Since15")
    private static String getNameOf(ClassLoader classLoader) {
        if (JavaVersion.current().isJava9Compatible()) {
            return classLoader.getName();
        }
        return "unknown-loader";
    }

    private static URL[] extractJava9AppClasspath() {
        String cp = System.getProperty(JAVA_CLASS_PATH);
        String[] elements = cp.split(File.pathSeparator);

        URL[] urls = new URL[elements.length];
        for (int i = 0; i < elements.length; i++) {
            try {
                URL url = new File(elements[i]).toURI().toURL();
                urls[i] = url;
            } catch (MalformedURLException mue) {
                throw throwAsUncheckedException(mue);
            }
        }
        return urls;
    }

    public void visitSpec(ClassLoaderSpec spec) {
    }

    public void visitClassPath(URL[] classPath) {
    }

    public void visitParent(ClassLoader classLoader) {
        visit(classLoader);
    }
}
