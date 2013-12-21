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

import java.net.URL;
import java.net.URLClassLoader;

public class ClassLoaderVisitor {
    private final ClassLoader stopAt = ClassLoader.getSystemClassLoader() == null ? null : ClassLoader.getSystemClassLoader().getParent();

    public void visit(ClassLoader classLoader) {
        if (classLoader == stopAt) {
            visitSpec(ClassLoaderSpec.SYSTEM_CLASS_LOADER);
            return;
        }

        if (classLoader instanceof ClassLoaderHierarchy) {
            ((ClassLoaderHierarchy) classLoader).visit(this);
        } else {
            if (classLoader instanceof URLClassLoader) {
                visitClassPath(((URLClassLoader) classLoader).getURLs());
            }
            if (classLoader.getParent() != null) {
                visitParent(classLoader.getParent());
            }
        }
    }

    public void visitSpec(ClassLoaderSpec spec) {
    }

    public void visitClassPath(URL[] classPath) {
    }

    public void visitParent(ClassLoader classLoader) {
        visit(classLoader);
    }
}
