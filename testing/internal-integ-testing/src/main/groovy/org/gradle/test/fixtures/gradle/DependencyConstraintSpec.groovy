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
class DependencyConstraintSpec {
    String group
    String module
    String version
    String preferredVersion
    String strictVersion
    List<String> rejects
    String reason
    Map<String, ?> attributes

    DependencyConstraintSpec(String g, String m, String v, String preferredVersion, String strictVersion, List<String> r, String desc, Map<String, ?> attrs) {
        group = g
        module = m
        version = v
        this.preferredVersion = preferredVersion
        this.strictVersion = strictVersion
        rejects = r?:Collections.<String>emptyList()
        reason = desc
        attributes = attrs
    }
}
