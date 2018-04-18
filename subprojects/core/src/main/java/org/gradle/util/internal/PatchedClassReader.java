/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.util.internal;

import org.gradle.api.JavaVersion;
import org.objectweb.asm.ClassReader;

public class PatchedClassReader extends ClassReader {
    public PatchedClassReader(byte[] bytes) {
        super(fixupClassVersion(bytes));
    }

    private static byte[] fixupClassVersion(byte[] classData) {
        byte[] tmp = classData;
        try {
            if (JavaVersion.forClass(classData) == JavaVersion.VERSION_11) {
                tmp = new byte[classData.length];
                System.arraycopy(classData, 0, tmp, 0, classData.length);
                // TODO let's pretend we're parsing a Java 10 class format
                tmp[7] = 54;
            }
            return tmp;
        } catch (ArrayIndexOutOfBoundsException ignored) {
            return classData;
        }
    }
}
