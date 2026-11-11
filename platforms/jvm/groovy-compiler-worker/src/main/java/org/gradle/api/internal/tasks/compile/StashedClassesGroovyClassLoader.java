/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.tasks.compile;

import groovy.lang.GroovyClassLoader;

import java.io.File;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.Map;

/**
 * The class loader of the AST transformations. A transformation may load classes of the previous output through it rather
 * than through {@code ClassNode#getTypeClass()}, so it defines the classes being recompiled from their stashed class files,
 * like {@link StashedClassesClassLoader} does for the compile classpath. Only the classes some class of the previous output
 * refers to are stashed for loading, so a transformation probing by name for a class that was removed does not find it.
 */
class StashedClassesGroovyClassLoader extends GroovyClassLoader {

    static {
        ClassLoader.registerAsParallelCapable();
    }

    private final Map<String, File> stashedClassFiles;

    StashedClassesGroovyClassLoader(ClassLoader parent, Map<String, File> stashedClassFiles) {
        super(parent, null);
        this.stashedClassFiles = stashedClassFiles;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        try {
            return super.findClass(name);
        } catch (ClassNotFoundException e) {
            return StashedClassesClassLoader.defineStashedClass(stashedClassFiles, name, e, new StashedClassesClassLoader.Definer() {
                @Override
                public void definePackage(String packageName) {
                    StashedClassesGroovyClassLoader.this.definePackage(packageName, null, null, null, null, null, null, null);
                }

                @Override
                public Class<?> defineClass(String className, byte[] bytes, CodeSource codeSource) {
                    return StashedClassesGroovyClassLoader.this.defineClass(className, bytes, 0, bytes.length, new ProtectionDomain(codeSource, null, StashedClassesGroovyClassLoader.this, null));
                }
            });
        }
    }
}
