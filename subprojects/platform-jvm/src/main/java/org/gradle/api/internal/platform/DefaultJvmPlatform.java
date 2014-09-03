/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.platform;

import org.gradle.api.Incubating;
import org.gradle.api.JavaVersion;
import org.gradle.jvm.internal.toolchain.JavaToolChainInternal;
import org.gradle.jvm.toolchain.JavaToolChain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Incubating
public class DefaultJvmPlatform implements JvmPlatform {
    private final JavaVersion targetCompatibility;

    public DefaultJvmPlatform(JavaVersion targetCompatibility) {
        this.targetCompatibility = targetCompatibility;
    }

    public JavaVersion getTargetCompatibility() {
        return targetCompatibility;
       }

    public List<String> getErrors(JavaToolChain toolChain) {
        JavaVersion version = ((JavaToolChainInternal) toolChain).getJavaVersion();
        if (targetCompatibility.compareTo(version) > 0) {
            return Arrays.asList("Could not use target JVM platform: '"+targetCompatibility+"' when using JDK: '"+version+"'. Change to a lower target.");
        }

        return new ArrayList<String>();
    }

    public String getName() {
        return "target JDK " + targetCompatibility;
    }

    public String toString() {
        return getName();
    }
}
