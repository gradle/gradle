/*
 * Copyright 2024 the original author or authors.
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

/**
 * Kept here since it's used in build logic. Should be removed once we upgrade the wrapper.
 *
 * Use {@link org.gradle.model.internal.asm.AsmConstants} instead.
 */
public class AsmConstants {
    public static final int ASM_LEVEL = org.gradle.model.internal.asm.AsmConstants.ASM_LEVEL;

    public static final int MIN_SUPPORTED_JAVA_VERSION = org.gradle.model.internal.asm.AsmConstants.MIN_SUPPORTED_JAVA_VERSION;

    public static final int MAX_SUPPORTED_JAVA_VERSION = org.gradle.model.internal.asm.AsmConstants.MAX_SUPPORTED_JAVA_VERSION;

    public static boolean isSupportedVersion(int javaMajorVersion) {
        return javaMajorVersion <= MAX_SUPPORTED_JAVA_VERSION;
    }
}
