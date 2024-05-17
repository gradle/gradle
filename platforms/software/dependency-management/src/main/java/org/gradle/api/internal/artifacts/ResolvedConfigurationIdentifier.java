/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.internal.artifacts;

import org.gradle.api.artifacts.ModuleVersionIdentifier;

public class ResolvedConfigurationIdentifier {
    private final ModuleVersionIdentifier id;
    private final String configuration;

    public ResolvedConfigurationIdentifier(ModuleVersionIdentifier moduleVersionIdentifier,
                                           String configuration) {
        this.id = moduleVersionIdentifier;
        this.configuration = configuration;
    }

    public String getConfiguration() {
        return configuration;
    }

    public String getModuleGroup() {
        return id.getGroup();
    }

    public String getModuleName() {
        return id.getName();
    }

    public String getModuleVersion() {
        return id.getVersion();
    }

    public ModuleVersionIdentifier getId() {
        return id;
    }

    @Override
    public String toString() {
        return String.format("%s:%s:%s:%s", getModuleGroup(), getModuleName(), getModuleVersion(), configuration);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ResolvedConfigurationIdentifier that = (ResolvedConfigurationIdentifier) o;

        if (!id.equals(that.id)) {
            return false;
        }
        return configuration.equals(that.configuration);
    }

    @Override
    public int hashCode() {
        return id.hashCode() ^ configuration.hashCode();
    }
}
