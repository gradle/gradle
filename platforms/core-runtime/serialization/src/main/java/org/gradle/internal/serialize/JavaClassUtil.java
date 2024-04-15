/*
 * Copyright 2023 the original author or authors.
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

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Utility class which operates directly on Java class files.
 */
public class JavaClassUtil {

    private static final int MAGIC_BYTES = 0xCAFEBABE;

    private JavaClassUtil() {
        // Private to prevent instantiation.
    }

    /**
     * Get the class file major version from the provided {@code file}.
     *
     * @throws IOException If the file does not exist or is malformed.
     */
    public static int getClassMajorVersion(File file) throws IOException {
        return getClassMajorVersion(new FileInputStream(file));
    }

    /**
     * Get the class file major version from the provided {@code javaClass}
     *
     * @throws IOException If there is an error reading the class file contents.
     */
    public static int getClassMajorVersion(Class<?> javaClass) throws IOException {
        return getClassMajorVersion(javaClass.getName(), javaClass.getClassLoader());
    }

    /**
     * Get the class file major version from the class with the given {@code name} by loading it
     * from the provided {@code loader}.
     *
     * @return null if the class cannot be loaded.
     *
     * @throws IOException If there is an error reading the class file contents.
     */
    public static Integer getClassMajorVersion(String name, ClassLoader loader) throws IOException {
        InputStream is = loader.getResourceAsStream(name.replace('.', '/') + ".class");
        if (is == null) {
            return null;
        }
        return getClassMajorVersion(is);
    }

    /**
     * Get the class file major version from class file data provided by {@code is}.
     * This method will close the provided {@link InputStream}.
     *
     * @throws IOException If the stream contents are malformed.
     */
    public static int getClassMajorVersion(InputStream is) throws IOException {
        DataInputStream data = new DataInputStream(is);
        try {
            if (MAGIC_BYTES != data.readInt()) {
                throw new IOException("Invalid .class file header");
            }
            data.readUnsignedShort(); // Minor
            return data.readUnsignedShort(); // Major
        } finally {
            data.close();
        }
    }
}
