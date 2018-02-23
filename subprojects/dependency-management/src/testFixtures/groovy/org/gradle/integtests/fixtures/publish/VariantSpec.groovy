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

    static class ArtifactSpec {
        String name
        String url
        String ext = 'jar'
    }
}
