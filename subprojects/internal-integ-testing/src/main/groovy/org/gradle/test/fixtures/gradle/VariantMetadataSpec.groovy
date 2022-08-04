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

@CompileStatic
class VariantMetadataSpec {
    String name
    Map<String, String> attributes
    List<DependencySpec> dependencies = []
    List<DependencyConstraintSpec> dependencyConstraints = []
    List<FileSpec> artifacts = []
    List<CapabilitySpec> capabilities = []
    AvailableAtSpec availableAt
    boolean useDefaultArtifacts = true
    boolean noArtifacts = false

    VariantMetadataSpec(String name, Map<String, String> attributes = [:]) {
        this.name = name
        this.attributes = attributes
    }

    VariantMetadataSpec(String name, Map<String, String> attributes, List<DependencySpec> dependencies, List<DependencyConstraintSpec> dependencyConstraints, List<FileSpec> artifacts, List<CapabilitySpec> capabilities, AvailableAtSpec availableAt) {
        this.name = name
        this.attributes = attributes
        this.dependencies = dependencies
        this.dependencyConstraints = dependencyConstraints
        this.artifacts = artifacts
        this.capabilities = capabilities
        this.availableAt = availableAt
    }

    void attribute(String name, String value) {
        if (attributes == null) {
            attributes = [:]
        }
        attributes[name] = value
    }

    void dependsOn(String group, String module, String version, String reason = null, Map<String, ?> attributes = [:]) {
        dependencies << new DependencySpec(group, module, version, reason, attributes)
    }

    void dependsOn(String group, String module, String version, @DelegatesTo(value=DependencySpec, strategy=Closure.DELEGATE_FIRST) Closure<?> config) {
        def spec = new DependencySpec(group, module, version)
        config.delegate = spec
        config.resolveStrategy = Closure.DELEGATE_FIRST
        config()
        dependencies += spec
    }

    void dependsOn(String notation) {
        def gav = notation.split(':')
        dependsOn(gav[0], gav[1], gav[2])
    }

    void dependsOn(String notation, @DelegatesTo(value=DependencySpec, strategy=Closure.DELEGATE_FIRST) Closure<?> config) {
        def gav = notation.split(':')
        dependsOn(gav[0], gav[1], gav[2], config)
    }

    void constraint(String group, String module, String version, String reason = null, Map<String, ?> attributes = [:]) {
        dependencyConstraints << new DependencyConstraintSpec(group, module, version, null, null, null, reason, attributes)
    }

    void constraint(String notation) {
        def gav = notation.split(':')
        constraint(gav[0], gav[1], gav[2])
    }

    void artifact(String name) {
        artifacts << new FileSpec(name)
    }

    void artifact(String name, String url) {
        artifacts << new FileSpec(name, url)
    }

    void artifact(String name, String url, String publishUrl) {
        artifacts << new FileSpec(name, url, publishUrl)
    }

    void capability(String group, String name, String version = '1.0') {
        capabilities << new CapabilitySpec(group, name, version)
    }

    void capability(String name) {
        capabilities << new CapabilitySpec('org.test', name, '1.0')
    }

    void availableAt(String url, String group, String name, String version) {
        availableAt = new AvailableAtSpec(url, group, name, version)
        noArtifacts = true
    }
}
