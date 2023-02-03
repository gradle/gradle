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

package org.gradle.nativeplatform;

import org.gradle.api.Incubating;
import org.gradle.api.Named;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.tasks.Input;

/**
 * Represents a target architecture of a component.  Typical architectures include "x86" and "x86-64".
 *
 * @since 5.1
 */
public abstract class MachineArchitecture implements Named {
    public static final Attribute<MachineArchitecture> ARCHITECTURE_ATTRIBUTE = Attribute.of("org.gradle.native.architecture", MachineArchitecture.class);

    /**
     * {@inheritDoc}
     */
    @Override
    @Input
    public abstract String getName();

    /**
     * The intel x86 32-bit architecture
     */
    public static final String X86 = "x86";

    /**
     * The intel x86 64-bit architecture
     */
    public static final String X86_64 = "x86-64";

    /**
     * The ARM 64-bit architecture
     *
     * @since 7.6
     */
    @Incubating
    public static final String ARM64 = "aarch64";
}
