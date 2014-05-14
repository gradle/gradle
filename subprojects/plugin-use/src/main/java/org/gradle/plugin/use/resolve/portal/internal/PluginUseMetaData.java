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

package org.gradle.plugin.use.resolve.portal.internal;

import org.gradle.api.GradleException;

import java.util.Map;

/**
 * Defines the JSON protocol for plugin portal responses to plugin metadata queries.
 */
class PluginUseMetaData {

    static final String M2_JAR = "M2_JAR";

    String id;
    String version;
    Map<String, String> implementation;
    String implementationType;

    void verify() {
        if (implementationType == null) {
            throw new GradleException("Invalid plugin metadata: No implementation type specified.");
        }
        if (!implementationType.equals(M2_JAR)) {
            throw new GradleException(String.format("Invalid plugin metadata: Unsupported implementation type: %s.", implementationType));
        }
        if (implementation == null) {
            throw new GradleException("Invalid plugin metadata: No implementation specified.");
        }
        if (implementation.get("gav") == null) {
            throw new GradleException("Invalid plugin metadata: No module coordinates specified.");
        }
        if (implementation.get("repo") == null) {
            throw new GradleException("Invalid plugin metadata: No module repository specified.");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        PluginUseMetaData that = (PluginUseMetaData) o;

        if (!id.equals(that.id)) {
            return false;
        }
        if (!implementation.equals(that.implementation)) {
            return false;
        }
        if (!implementationType.equals(that.implementationType)) {
            return false;
        }
        if (!version.equals(that.version)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = id.hashCode();
        result = 31 * result + version.hashCode();
        result = 31 * result + implementation.hashCode();
        result = 31 * result + implementationType.hashCode();
        return result;
    }
}
