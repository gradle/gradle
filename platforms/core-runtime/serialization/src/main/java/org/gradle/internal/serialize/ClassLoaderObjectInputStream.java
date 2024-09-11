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
package org.gradle.internal.serialize;

import org.gradle.api.JavaVersion;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;

public class ClassLoaderObjectInputStream extends ObjectInputStream {
    private final ClassLoader loader;

    public ClassLoaderObjectInputStream(InputStream in, ClassLoader loader) throws IOException {
        super(in);
        this.loader = loader;
    }

    public ClassLoader getClassLoader() {
        return loader;
    }

    @Override
    protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
        try {
            return Class.forName(desc.getName(), false, loader);
        } catch (ClassNotFoundException e) {
            return super.resolveClass(desc);
        } catch (UnsupportedClassVersionError e) {
            try {
                Integer majorVersion = JavaClassUtil.getClassMajorVersion(desc.getName(), loader);
                if (majorVersion != null) {
                    throw new UnsupportedClassVersionErrorWithJavaVersion(e, JavaVersion.forClassVersion(majorVersion));
                }
                // We could not find the class. Throw the original error.
                throw e;
            } catch (IOException ignored) {
                // There was an error parsing the class. Throw the original error.
                throw e;
            }
        }
    }

    /**
     * Specialization of {@link UnsupportedClassVersionError} which includes the {@link JavaVersion} of
     * the class which is unsupported. The base class only includes the class version in the error message
     * and does not provide programmatic access.
     */
    public static class UnsupportedClassVersionErrorWithJavaVersion extends UnsupportedClassVersionError  {
        private final JavaVersion version;
        public UnsupportedClassVersionErrorWithJavaVersion(UnsupportedClassVersionError cause, JavaVersion version) {
            super(cause.getMessage());
            initCause(cause);
            this.version = version;
        }

        public JavaVersion getVersion() {
            return version;
        }
    }
}
