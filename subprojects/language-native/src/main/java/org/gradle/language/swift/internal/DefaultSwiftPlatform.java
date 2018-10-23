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

package org.gradle.language.swift.internal;

import org.gradle.language.swift.SwiftPlatform;
import org.gradle.nativeplatform.OperatingSystemFamily;
import org.gradle.nativeplatform.TargetMachine;
import org.gradle.nativeplatform.platform.internal.ImmutableDefaultNativePlatform;
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal;

public class DefaultSwiftPlatform extends ImmutableDefaultNativePlatform implements SwiftPlatform {
    private final OperatingSystemFamily operatingSystemFamily;

    public DefaultSwiftPlatform(String name, TargetMachine targetMachine, NativePlatformInternal targetNativePlatform) {
        super(name, targetNativePlatform.getOperatingSystem(), targetNativePlatform.getArchitecture());
        this.operatingSystemFamily = targetMachine.getOperatingSystemFamily();
    }

    @Override
    public OperatingSystemFamily getOperatingSystemFamily() {
        return operatingSystemFamily;
    }
}
