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

package org.gradle.integtests.fixtures.publish

import groovy.transform.CompileStatic

@CompileStatic
class VariantSpec {
    String name
    List<Object> dependsOn = []
    List<Object> constraints = []
    Map<String, String> attributes = [:]
    List<ArtifactSpec> artifacts = []
    List<CapabilitySpec> capabilities = []
    boolean noArtifacts = false

    void dependsOn(coord) {
        dependsOn << coord
    }

    void constraint(coord) {
        constraints << coord
    }

    void attribute(String name, String value) {
        attributes[name] = value
    }

    void artifact(String name) {
        artifacts << new ArtifactSpec(name: name)
    }

    void artifact(String name, String url) {
        artifacts << new ArtifactSpec(name: name, url: url)
    }

    void capability(String group, String name, String version = '1.0') {
        capabilities << new CapabilitySpec(group:group, name:name, version:version)
    }

    void capability(String name) {
        capabilities << new CapabilitySpec(group: 'org.test', name: name, version: '1.0')
    }

    void dependsOn(String notation, @DelegatesTo(value=org.gradle.test.fixtures.gradle.DependencySpec, strategy=Closure.DELEGATE_FIRST)  Closure<?> configuration) {
        def gav = notation.split(':')
        doDependOn(gav[0], gav[1], gav[2], configuration)
    }

    void dependsOn(String group, String name, String version, @DelegatesTo(value=org.gradle.test.fixtures.gradle.DependencySpec, strategy=Closure.DELEGATE_FIRST)  Closure<?> configuration) {
        doDependOn(group, name, version, configuration)
    }

    private void doDependOn(String group, String name, String version, @DelegatesTo(value=org.gradle.test.fixtures.gradle.DependencySpec, strategy=Closure.DELEGATE_FIRST)  Closure<?> configuration) {
        dependsOn << new DependencySpec(group: group, name: name, version: version, configuration: configuration)
    }

    static class ArtifactSpec {
        String name
        String url
        String ext = 'jar'
    }

    static class CapabilitySpec {
        String group
        String name
        String version
    }

    static class DependencySpec {
        String group
        String name
        String version
        Closure<?> configuration
    }
}
