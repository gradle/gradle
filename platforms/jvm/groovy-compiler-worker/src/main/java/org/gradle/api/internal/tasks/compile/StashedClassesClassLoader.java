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

import org.gradle.internal.classloader.VisitableURLClassLoader;
import org.gradle.internal.classpath.ClassPath;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.util.Map;

/**
 * The class loader for the compile classpath of an incremental Groovy compilation.
 * <p>
 * The destination directory is on the compile classpath, but the class files of the classes being recompiled have been
 * moved out of it, to the stash of the compile transaction. The Groovy compiler loads classes of the previous compilation
 * from that classpath: the helper classes of precompiled traits, and whatever an AST transformation asks for through
 * {@code ClassNode#getTypeClass()}. When the JVM links such a class, or reflects on its members, it resolves every class it
 * refers to, whatever the visibility of the reference, and fails with a {@code NoClassDefFoundError} when one of them is
 * being recompiled. This loader defines those classes from their stashed class files instead.
 * <p>
 * The stashed class files are not resources of this loader, so the compiler, which resolves classes from their bytecode,
 * does not see them, and {@link CompileClasspathGroovyClassLoader} keeps it from loading them either.
 */
class StashedClassesClassLoader extends VisitableURLClassLoader {

    static {
        ClassLoader.registerAsParallelCapable();
    }

    private final Map<String, File> stashedClassFiles;

    StashedClassesClassLoader(String name, ClassLoader parent, ClassPath classPath, Map<String, File> stashedClassFiles) {
        super(name, parent, classPath);
        this.stashedClassFiles = stashedClassFiles;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        try {
            return super.findClass(name);
        } catch (ClassNotFoundException e) {
            return defineStashedClass(stashedClassFiles, name, e, new Definer() {
                @Override
                public void definePackage(String packageName) {
                    StashedClassesClassLoader.this.definePackage(packageName, null, null, null, null, null, null, null);
                }

                @Override
                public Class<?> defineClass(String className, byte[] bytes, CodeSource codeSource) {
                    return StashedClassesClassLoader.this.defineClass(className, bytes, 0, bytes.length, new ProtectionDomain(codeSource, null, StashedClassesClassLoader.this, null));
                }
            });
        }
    }

    /**
     * The protected methods of a class loader that defining a class needs.
     */
    interface Definer {
        void definePackage(String packageName);

        Class<?> defineClass(String className, byte[] bytes, CodeSource codeSource);
    }

    /**
     * Defines the class from its stashed class file, with its package, which defining a class from bytes does not define on
     * Java 8, and with the stash directory as its code source. Throws the given exception when the class is not stashed.
     */
    static Class<?> defineStashedClass(Map<String, File> stashedClassFiles, String name, ClassNotFoundException notStashed, Definer definer) throws ClassNotFoundException {
        File stashedClassFile = stashedClassFiles.get(name);
        if (stashedClassFile == null) {
            throw notStashed;
        }
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(stashedClassFile.toPath());
        } catch (IOException e) {
            throw new ClassNotFoundException(name, e);
        }
        int lastDot = name.lastIndexOf('.');
        if (lastDot > 0) {
            try {
                definer.definePackage(name.substring(0, lastDot));
            } catch (IllegalArgumentException alreadyDefined) {
                // by a class of the package loaded earlier, from the classpath or the stash
            }
        }
        return definer.defineClass(name, bytes, codeSourceOf(stashedClassFile));
    }

    private static CodeSource codeSourceOf(File stashedClassFile) {
        try {
            return new CodeSource(stashedClassFile.getParentFile().toURI().toURL(), (Certificate[]) null);
        } catch (MalformedURLException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String classFileName(String className) {
        return className.replace('.', '/') + ".class";
    }
}
