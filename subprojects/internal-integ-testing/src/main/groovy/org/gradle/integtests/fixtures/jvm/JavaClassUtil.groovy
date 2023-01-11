/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.integtests.fixtures.jvm

import groovy.transform.CompileStatic
import org.gradle.api.JavaVersion

@CompileStatic
class JavaClassUtil {

    private static final int MAGIC_BYTES = (int) 0xCAFEBABE
    private static final int CLASS_MAJOR_VERSION_OFFSET = 44

    static int getClassMajorVersion(Class<?> javaClass) {
        URL url = classFileUrlOf(javaClass)
        DataInputStream data = new DataInputStream(url.openStream())
        try {
            if (MAGIC_BYTES != data.readInt()) {
                throw new IOException("Invalid .class file header for '$javaClass' in '${url}'")
            }
            data.readUnsignedShort() // minor
            return data.readUnsignedShort() // major
        } finally {
            data.close()
        }
    }

    private static URL classFileUrlOf(Class<?> javaClass) {
        return javaClass.classLoader.getResource(classpathPathFor(javaClass))
    }

    private static String classpathPathFor(Class<?> javaClass) {
        return javaClass.name.replace('.', '/') + '.class'
    }

    static int getClassMajorVersion(JavaVersion javaVersion) {
        if (javaVersion == JavaVersion.VERSION_HIGHER) {
            throw new UnsupportedOperationException("Unable to provide class file major version for '$javaVersion'")
        }
        return javaVersion.majorVersion.toInteger() + CLASS_MAJOR_VERSION_OFFSET
    }

    private JavaClassUtil() {}
}
