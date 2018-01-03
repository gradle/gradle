/*
 * Copyright 2017 the original author or authors.
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

/**
 * A <b>temporary workaround</b> for reading Java 10 class files. This special class reader will pretend that Java 10
 * classes are Java 9 classes, making it possible to read them.
 */
public class PatchedClassReader extends ClassReader {
    public PatchedClassReader(byte[] b) {
        super(fixupClassVersion(b));
    }

    private static byte[] fixupClassVersion(byte[] classData) {
        byte[] tmp = classData;
        if (JavaVersion.forClass(classData) == JavaVersion.VERSION_1_10) {
            tmp = new byte[classData.length];
            System.arraycopy(classData, 0, tmp, 0, classData.length);
            // TODO: BM, until ASM6.1-BETA is out, let's pretend we're parsing a Java 9 class format
            tmp[7] = 53;
        }
        return tmp;
    }
}
