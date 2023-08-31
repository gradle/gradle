/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.plugins.jvm.internal;

import org.gradle.api.artifacts.Dependency;

import java.util.Collections;

abstract class AbstractJUnitPlatformTestEngineToolchain<T extends JUnitPlatformToolchain.Parameters> extends JUnitPlatformToolchain<T> {
    private final String groupName;

    public AbstractJUnitPlatformTestEngineToolchain(String groupName) {
        super();
        this.groupName = groupName;
    }

    protected abstract String getVersion();

    @Override
    public Iterable<Dependency> getImplementationDependencies() {
        return Collections.singletonList(getDependencyFactory().create(groupName + ":" + getVersion()));
    }
}
