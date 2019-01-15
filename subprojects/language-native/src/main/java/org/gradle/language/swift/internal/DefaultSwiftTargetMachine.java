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
import org.gradle.language.swift.SwiftVersion;
import org.gradle.nativeplatform.TargetMachine;
import org.gradle.nativeplatform.internal.DefaultTargetMachine;

public class DefaultSwiftTargetMachine extends DefaultTargetMachine implements SwiftPlatform {
    private final SwiftVersion sourceCompatibility;

    public DefaultSwiftTargetMachine(TargetMachine targetMachine, SwiftVersion sourceCompatibility) {
        super(targetMachine.getOperatingSystemFamily(), targetMachine.getArchitecture());
        this.sourceCompatibility = sourceCompatibility;
    }

    @Override
    public SwiftVersion getSourceCompatibility() {
        return sourceCompatibility;
    }
}
