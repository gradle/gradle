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
import org.codehaus.groovy.control.CompilationFailedException;

import java.io.File;
import java.util.Map;

/**
 * The Groovy class loader over the compile classpath loader, through which the compiler loads classes.
 * <p>
 * The compiler resolves classes from their bytecode and falls back to loading them. The classes being recompiled are
 * defined from their stashed class files for the sake of the JVM, see {@link StashedClassesClassLoader}, but the compiler
 * must not resolve one that way: a class being recompiled is resolved from its source, and a class removed from a changed
 * source has to stay unresolved. So a stashed class whose class file is not on the classpath cannot be loaded through
 * this loader, while the parent loader still defines it for the JVM.
 */
class CompileClasspathGroovyClassLoader extends GroovyClassLoader {

    static {
        ClassLoader.registerAsParallelCapable();
    }

    private final Map<String, File> stashedClassFiles;

    CompileClasspathGroovyClassLoader(ClassLoader parent, Map<String, File> stashedClassFiles) {
        super(parent, null);
        this.stashedClassFiles = stashedClassFiles;
    }

    @Override
    public Class<?> loadClass(String name, boolean lookupScriptFiles, boolean preferClassOverScript, boolean resolve) throws ClassNotFoundException, CompilationFailedException {
        if (stashedClassFiles.containsKey(name) && getResource(StashedClassesClassLoader.classFileName(name)) == null) {
            throw new ClassNotFoundException(name);
        }
        return super.loadClass(name, lookupScriptFiles, preferClassOverScript, resolve);
    }
}
