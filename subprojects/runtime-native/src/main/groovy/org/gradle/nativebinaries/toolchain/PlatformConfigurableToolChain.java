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
package org.gradle.nativebinaries.toolchain;

import org.gradle.api.Action;
import org.gradle.api.Incubating;

/**
 * A ToolChain that can handle additional platforms simply by configuring the NativeBinary.
 */
@Incubating
public interface PlatformConfigurableToolChain extends ToolChain {

    /**
     * Add support for target platform specified by name.
     */
    public void target(String platformName);

    /**
     * Add configuration for a target platform specified by name with additional configuration action.
     */
    public void target(String platformName, Action<? super TargetedPlatformToolChain> action);
}
