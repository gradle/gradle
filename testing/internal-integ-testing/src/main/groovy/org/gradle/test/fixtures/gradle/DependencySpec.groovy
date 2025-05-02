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

package org.gradle.test.fixtures.gradle

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import org.gradle.api.attributes.Category

@CompileStatic
@EqualsAndHashCode
class DependencySpec {
    String group
    String module
    String version
    String preferredVersion
    String strictVersion
    List<String> rejects
    List<ExcludeSpec> exclusions = []
    boolean endorseStrictVersions
    String reason
    Map<String, Object> attributes
    List<CapabilitySpec> requestedCapabilities = []
    ArtifactSelectorSpec artifactSelector

    DependencySpec(String g, String m, String v, String preferredVersion, String strictVersion, List<String> rejects, Collection<Map> excludes, Boolean endorseStrictVersions, String reason, Map<String, Object> attributes, ArtifactSelectorSpec artifactSelector, String requestedCapability) {
        group = g
        module = m
        version = v
        this.preferredVersion = preferredVersion
        this.strictVersion = strictVersion
        this.rejects = rejects?:Collections.<String>emptyList()
        if (excludes) {
            exclusions = excludes.collect { Map exclusion ->
                String group = exclusion.get('group')?.toString()
                String module = exclusion.get('module')?.toString()
                new ExcludeSpec(group, module)
            }
        }
        this.endorseStrictVersions = endorseStrictVersions
        this.reason = reason
        this.attributes = attributes
        this.artifactSelector = artifactSelector
        if (requestedCapability) {
            def parts = requestedCapability.split(':')
            this.requestedCapabilities << new CapabilitySpec(parts[0], parts[1], parts.size() > 2 ? parts[2] : null)
        }
    }

    DependencySpec(String g, String m, String v, String reason, Map<String, Object> attributes) {
        group = g
        module = m
        version = v
        this.reason = reason
        this.attributes = attributes
        if (!attributes?.isEmpty()) {
            def category = attributes[Category.CATEGORY_ATTRIBUTE.name]
            if (category == Category.REGULAR_PLATFORM || category == Category.ENFORCED_PLATFORM) {
                this.endorseStrictVersions = true
            }
        }
        rejects = []
    }

    DependencySpec(String g, String m, String v) {
        group = g
        module = m
        version = v
        attributes = [:]
        rejects = []
    }

    DependencySpec attribute(String name, Object value) {
        attributes[name] = value
        this
    }

    DependencySpec exclude(String group, String module) {
        exclusions << new ExcludeSpec(group, module)
        this
    }

    DependencySpec requestedCapability(String group, String name, String version) {
        requestedCapabilities << new CapabilitySpec(group, name, version)
        this
    }
}
