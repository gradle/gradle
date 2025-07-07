/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.artifacts.dependencies

import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.internal.file.FileCollectionInternal
import spock.lang.Specification

class DefaultFileCollectionDependencyTest extends Specification {
    private final FileCollectionInternal source = Mock()
    private final ComponentIdentifier targetComponent = Mock()
    private final DefaultFileCollectionDependency dependency = new DefaultFileCollectionDependency(targetComponent, source)

    def defaultValues() {
        expect:
        dependency.group == null
        dependency.name == "unspecified"
        dependency.version == null
    }

    def createsCopy() {
        when:
        DefaultFileCollectionDependency copy = dependency.copy()

        then:
        copy.targetComponentId == targetComponent
        copy.files == source
    }

}
