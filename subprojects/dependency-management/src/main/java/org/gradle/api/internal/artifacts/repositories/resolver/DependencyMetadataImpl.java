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

package org.gradle.api.internal.artifacts.repositories.resolver;

import org.gradle.api.artifacts.DependencyMetadata;

public class DependencyMetadataImpl implements DependencyMetadata {
    private final String group;
    private final String name;
    private String version;

    public DependencyMetadataImpl(String group, String name, String version) {
        this.group = group;
        this.name = name;
        this.version = version;
    }

    @Override
    public String getGroup() {
        return this.group;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public String getVersion() {
        return this.version;
    }

    @Override
    public DependencyMetadata setVersion(String version) {
        this.version = version;
        return this;
    }
}
