/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.jvm.toolchain.internal


import org.gradle.api.internal.file.IdentityFileResolver
import spock.lang.Specification
import spock.lang.Subject

class LocationListInstallationSupplierTest extends Specification {

    final buildOptions = Mock(ToolchainConfiguration)

    @Subject
    def supplier = new LocationListInstallationSupplier(buildOptions, new IdentityFileResolver())

    def "supplies no installations for empty property"() {
        when:
        buildOptions.installationsFromPaths >> []
        def directories = supplier.get()

        then:
        directories.isEmpty()
    }

    def "supplies single installations for single path"() {
        when:
        buildOptions.installationsFromPaths >> ["/foo/bar"]
        def directories = supplier.get()

        then:
        directories.size() == 1
        directories[0].location == new File("/foo/bar")
        directories[0].source == "Gradle property 'org.gradle.java.installations.paths'"
    }

    def "supplies multiple installations for multiple paths"() {
        when:
        buildOptions.installationsFromPaths >> ["/foo/bar", "/foo/123"]
        def directories = supplier.get()

        then:
        directories.size() == 2
        directories*.location.containsAll(new File("/foo/bar"), new File("/foo/123"))
        directories*.source.unique() == [ "Gradle property 'org.gradle.java.installations.paths'" ]
    }
}
