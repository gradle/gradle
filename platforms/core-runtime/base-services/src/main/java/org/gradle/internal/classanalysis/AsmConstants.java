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

package org.gradle.internal.classanalysis;

import org.objectweb.asm.Opcodes;

public class AsmConstants {
    public static final int ASM_LEVEL = Opcodes.ASM9;

    /**
     * The minimal version of Java for which ASM understands the bytecodes.
     */
    public static final int MIN_SUPPORTED_JAVA_VERSION = 1;

    /**
     * The latest version of Java for which ASM understands the bytecodes.
     *
     * Updated for ASM 9.5.
     *
     * @see <a href="https://asm.ow2.io/versions.html">ASM release notes</a>
     */
    public static final int MAX_SUPPORTED_JAVA_VERSION = 21;
}
