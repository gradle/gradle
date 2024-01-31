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
     * The latest version of Java for which ASM understands the bytecodes.
     * <p>
     * Updated for ASM 9.6.
     *
     * @see <a href="https://asm.ow2.io/versions.html">ASM release notes</a>
     * Note that this does not mean that Java 21 is supported, just that ASM can parse the bytecode.
     * @see <a href="https://github.com/gradle/gradle/issues/27856">the issue</a>.
     */
    public static final int MAX_SUPPORTED_JAVA_VERSION = 22;
}
