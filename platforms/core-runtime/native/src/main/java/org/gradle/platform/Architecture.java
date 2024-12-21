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

package org.gradle.platform;

import com.google.common.base.Ascii;
import org.gradle.api.GradleException;
import org.gradle.api.Incubating;

/**
 * Constants for various processor architectures Gradle runs on.
 *
 * @since 7.6
 */
@Incubating
public enum Architecture {
    /**
     * 32-bit complex instruction set computer (CISC) architectures, including "x32", "i386", "x86"..
     */
    X86,

    /**
     * 64-bit variant of the X86 instruction set, including "x64", "x86_64", "amd64", "ia64".
     */
    X86_64,

    /**
     * 64-bit reduced instruction set computer (RISC) architectures, including "aarch64", "arm64".
     */
    AARCH64;

    /**
     * Get the architecture of the current system.
     *
     * @since 8.13
     */
    public static Architecture current() {
        String arch = System.getProperty("os.arch", "none");
        String archName = Ascii.toLowerCase(arch);
        if (archName.equals("x86")) {
            return X86;
        } else if (archName.equals("amd64") || archName.equals("x86_64")) {
            return X86_64;
        } else if (archName.equals("aarch64")) {
            return AARCH64;
        } else {
            throw new GradleException("Unhandled system architecture: " + arch);
        }
    }
}
