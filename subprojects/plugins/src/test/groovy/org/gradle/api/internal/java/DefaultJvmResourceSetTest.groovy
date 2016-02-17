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
package org.gradle.api.internal.java
import org.gradle.api.file.SourceDirectorySet
import org.gradle.platform.base.internal.DefaultComponentSpecIdentifier
import spock.lang.Specification

class DefaultJvmResourceSetTest extends Specification {
    def "has useful String representation"() {
        def resourceSet = new DefaultJvmResourceSet(new DefaultComponentSpecIdentifier("project", "resourcesX"), Stub(SourceDirectorySet))

        expect:
        resourceSet.displayName == "JVM resources 'resourcesX'"
        resourceSet.toString() == "JVM resources 'resourcesX'"
    }
}
