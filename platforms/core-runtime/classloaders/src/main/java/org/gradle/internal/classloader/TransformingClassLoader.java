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

import com.google.common.io.ByteStreams;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.GradleException;
import org.gradle.internal.classpath.ClassPath;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.CodeSource;
import java.security.cert.Certificate;
import java.util.Collection;

public abstract class TransformingClassLoader extends VisitableURLClassLoader {
    static {
        try {
            ClassLoader.registerAsParallelCapable();
        } catch (NoSuchMethodError ignore) {
            // Not supported on Java 6
        }
    }

    public TransformingClassLoader(String name, ClassLoader parent, ClassPath classPath) {
        super(name, parent, classPath);
    }

    public TransformingClassLoader(String name, ClassLoader parent, Collection<URL> urls) {
        super(name, parent, urls);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        if (!shouldTransform(name)) {
            return super.findClass(name);
        }

        String resourceName = name.replace('.', '/') + ".class";
        URL resource = findResource(resourceName);

        byte[] bytes;
        CodeSource codeSource;
        try {
            if (resource != null) {
                bytes = loadBytecode(resource);
                bytes = transform(name, bytes);
                URL codeBase = ClasspathUtil.getClasspathForResource(resource, resourceName).toURI().toURL();
                codeSource = new CodeSource(codeBase, (Certificate[]) null);
            } else {
                bytes = generateMissingClass(name);
                codeSource = null;
            }
        } catch (Exception e) {
            throw new GradleException(String.format("Could not load class '%s' from %s.", name, resource), e);
        }

        if (bytes == null) {
            throw new ClassNotFoundException(name);
        }

        String packageName = StringUtils.substringBeforeLast(name, ".");
        Package p = getPackage(packageName);
        if (p == null) {
            try {
                definePackage(packageName, null, null, null, null, null, null, null);
            } catch (IllegalArgumentException iae) {
                // Another thread has beaten us while trying to register this package.
                // All do it with the same parameters so we can just keep going.
                // Let's still check that the package is there, just in case.
                if (getPackage(packageName) == null) {
                    // This should never happen.
                    throw new AssertionError("Package '" + packageName + "' cannot be defined but is not registered either in " + this, iae);
                }
            }
        }
        return defineClass(name, bytes, 0, bytes.length, codeSource);
    }

    @Override
    @SuppressWarnings("deprecation") // We still need to support Java 8 where non-deprecated version is not available.
    protected @Nullable Package getPackage(String name) {
        return super.getPackage(name);
    }

    protected byte @Nullable [] generateMissingClass(String name) {
        return null;
    }

    private byte[] loadBytecode(URL resource) throws IOException {
        InputStream inputStream = resource.openStream();
        try {
            return ByteStreams.toByteArray(inputStream);
        } finally {
            inputStream.close();
        }
    }

    protected boolean shouldTransform(String className) {
        return true;
    }

    protected abstract byte[] transform(String className, byte[] bytes);
}
