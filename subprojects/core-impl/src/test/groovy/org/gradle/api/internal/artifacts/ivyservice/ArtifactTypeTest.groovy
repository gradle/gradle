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

package org.gradle.api.internal.artifacts.ivyservice

import org.gradle.api.internal.component.ArtifactType
import spock.lang.Specification

class ArtifactTypeTest extends Specification {
    def "have sensible toString values"() {
        expect:
        ArtifactType.SOURCES.toString() == "'sources' artifacts"
        ArtifactType.JAVADOC.toString() == "'javadoc' artifacts"
        ArtifactType.IVY_DESCRIPTOR.toString() == "'ivy descriptor' artifacts"
        ArtifactType.MAVEN_POM.toString() == "'maven pom' artifacts"
    }
}
