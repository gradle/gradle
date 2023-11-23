/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.nativeplatform.platform.internal;

import org.gradle.api.tasks.Internal;
import org.gradle.nativeplatform.platform.Architecture;

public interface ArchitectureInternal extends Architecture {
    enum InstructionSet { X86, ITANIUM, PPC, SPARC, ARM }

    @Internal
    boolean isI386();

    @Internal
    boolean isAmd64();

    @Internal
    boolean isIa64();

    @Internal
    default boolean isArm() {
        return isArm32() || isArm64();
    }

    @Internal
    boolean isArm32();

    @Internal
    boolean isArm64();
}
