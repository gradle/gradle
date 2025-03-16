/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.test.fixtures.maven

class MavenDependency {
    String groupId
    String artifactId
    String version
    String classifier
    String type
    boolean optional
    Collection<MavenDependencyExclusion> exclusions = []

    MavenDependency hasType(def type) {
        assert this.type == type
        return this
    }

    @Override
    public String toString() {
        return String.format("MavenDependency %s:%s:%s:%s@%s", groupId, artifactId, version, classifier, type)
    }

    String getKey() {
        def key = "${groupId}:${artifactId}:${version}"
        if (classifier) {
            key += ":${classifier}"
        }
        key
    }
}
