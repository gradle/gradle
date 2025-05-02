/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.nativeplatform;

/**
 * A builder for configuring the architecture of a {@link TargetMachine} objects.
 *
 * @since 5.2
 */
public interface TargetMachineBuilder extends TargetMachine {
    /**
     * Returns a {@link TargetMachine} for the operating system of this machine and the x86 32-bit architecture
     */
    TargetMachine getX86();

    /**
     * Returns a {@link TargetMachine} for the operating system of this machine and the x86 64-bit architecture
     */
    TargetMachine getX86_64();

    /**
     * Returns a {@link TargetMachine} for the operating system of this machine and the specified architecture.
     */
    TargetMachine architecture(String architecture);
}
