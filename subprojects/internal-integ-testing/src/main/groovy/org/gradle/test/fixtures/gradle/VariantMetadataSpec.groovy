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

    VariantMetadataSpec(String name, Map<String, String> attributes = [:]) {
        this.name = name
        this.attributes = attributes
    }

    VariantMetadataSpec(String name, Map<String, String> attributes, List<DependencySpec> dependencies, List<DependencyConstraintSpec> dependencyConstraints, List<FileSpec> artifacts, List<CapabilitySpec> capabilities) {
        this.name = name
        this.attributes = attributes
        this.dependencies = dependencies
        this.dependencyConstraints = dependencyConstraints
        this.artifacts = artifacts
        this.capabilities = capabilities
    }

    void attribute(String name, String value) {
        if (attributes == null) {
            attributes = [:]
        }
        attributes[name] = value
    }

    void dependsOn(String group, String module, String version, String reason = null, Map<String, ?> attributes=[:]) {
        dependencies += new DependencySpec(group, module, version, null, null, null, reason, attributes)
    }

    void dependsOn(String group, String module, String version, @DelegatesTo(value=DependencySpec, strategy=Closure.DELEGATE_FIRST) Closure<?> config) {
        def spec = new DependencySpec(group, module, version, null, null, null, null, [:])
        config.delegate = spec
        config.resolveStrategy = Closure.DELEGATE_FIRST
        config()
        dependencies += spec
    }
}
