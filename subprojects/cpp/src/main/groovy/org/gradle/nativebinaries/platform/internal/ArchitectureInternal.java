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
package org.gradle.nativebinaries.platform.internal;

import org.gradle.nativebinaries.platform.Architecture;

public interface ArchitectureInternal extends Architecture {
    static final ArchitectureInternal TOOL_CHAIN_DEFAULT = new DefaultArchitecture("default", null, 0);

    enum InstructionSet { X86, ITANIUM, PPC, SPARC, ARM }

    InstructionSet getInstructionSet();

    int getRegisterSize();

    boolean isI386();

    boolean isAmd64();

    boolean isIa64();

    boolean isArm();

    boolean isArmv8();
}
