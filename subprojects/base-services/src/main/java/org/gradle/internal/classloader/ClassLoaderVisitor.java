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

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;

public class ClassLoaderVisitor {
    private static final String JAVA_CLASS_PATH = "java.class.path";
    private final ClassLoader stopAt = ClassLoader.getSystemClassLoader() == null ? null : ClassLoader.getSystemClassLoader().getParent();

    public void visit(ClassLoader classLoader) {
        if (classLoader == stopAt) {
            visitSpec(SystemClassLoaderSpec.INSTANCE);
            return;
        }

        if (classLoader instanceof ClassLoaderHierarchy) {
            ((ClassLoaderHierarchy) classLoader).visit(this);
        } else {
            if (classLoader instanceof URLClassLoader) {
                visitClassPath(((URLClassLoader) classLoader).getURLs());
            } else {
                visitClassPath(getClasspathURLs());
            }
            if (classLoader.getParent() != null) {
                visitParent(classLoader.getParent());
            }
        }
    }

    private URL[] getClasspathURLs() {
        String cp = System.getProperty(JAVA_CLASS_PATH);
        String[] elements = cp.split(File.pathSeparator);

        URL[] urls = new URL[elements.length];
        for (int i = 0; i < elements.length; i++) {
            try {
                URL url = new File(elements[i]).toURI().toURL();
                urls[i] = url;
            } catch (MalformedURLException ignore) {
                //ignore
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
