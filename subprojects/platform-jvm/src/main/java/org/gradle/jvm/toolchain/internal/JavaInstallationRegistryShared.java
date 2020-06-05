/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.jvm.toolchain.internal;

import org.gradle.jvm.toolchain.LogicalJavaInstallation;

import java.util.ArrayList;
import java.util.List;

// this acts as shared registry;
// * settings will have a rw-view through the container,
// * buildscripts will have ro-view through the toolchain support
public class JavaInstallationRegistryShared {

    // ASK: is concurrency is a concern here? Is it safe to assume settings
    // is evaluated before configuration phase?
    private List<LogicalJavaInstallation> installations = new ArrayList<>();

    public List<LogicalJavaInstallation> getAllInstallations() {
        return installations;
    }

    public void add(LogicalJavaInstallation installation) {
        installations.add(installation);
    }
}
