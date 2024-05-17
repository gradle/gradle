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

package org.gradle.language.nativeplatform;

import org.gradle.api.component.SoftwareComponent;
import org.gradle.api.provider.Provider;
import org.gradle.nativeplatform.TargetMachine;
import org.gradle.nativeplatform.toolchain.NativeToolChain;

/**
 * Represents a component that produces outputs that run on a native platform.
 *
 * @since 4.5
 */
public interface ComponentWithNativeRuntime extends SoftwareComponent {
    /**
     * Returns the base name of this component. This is used to calculate output file names.
     */
    Provider<String> getBaseName();

    /**
     * Returns true if this component has debugging enabled.
     */
    boolean isDebuggable();

    /**
     * Returns true if this component is optimized.
     */
    boolean isOptimized();

    /**
     * Returns the target machine for this component.
     *
     * @since 5.2
     */
    TargetMachine getTargetMachine();

    /**
     * Returns the tool chain for this component.
     */
    NativeToolChain getToolChain();
}
