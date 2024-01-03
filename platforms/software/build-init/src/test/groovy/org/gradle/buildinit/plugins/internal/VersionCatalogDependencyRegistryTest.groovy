/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.buildinit.plugins.internal

import spock.lang.Specification
import spock.lang.Unroll

class VersionCatalogDependencyRegistryTest extends Specification {

    def "tracks library versions"() {
        setup:
        def registry = new VersionCatalogDependencyRegistry(true)

        when:
        registry.registerLibrary("group:artifact", "1.1")

        then:
        registry.versions[0].alias == "group-artifact"
        registry.versions[0].module == "group:artifact"
        registry.versions[0].version == "1.1"
        registry.libraries[0].alias == "group-artifact"
        registry.libraries[0].module == "group:artifact"
        registry.libraries[0].version == "1.1"
        registry.libraries[0].versionRef == "group-artifact"
    }

    def "merges multiple libraries when encountering identical coordinates"() {
        setup:
        def registry = new VersionCatalogDependencyRegistry(true)

        when:
        registry.registerLibrary("group:artifact", "1.1")
        registry.registerLibrary("group:artifact", "1.1")

        then:
        registry.versions.size() == 1
        registry.libraries.size() == 1
        registry.versions[0].alias == "group-artifact"
        registry.versions[0].module == "group:artifact"
        registry.versions[0].version == "1.1"
        registry.libraries[0].alias == "group-artifact"
        registry.libraries[0].module == "group:artifact"
        registry.libraries[0].version == "1.1"
        registry.libraries[0].versionRef == "group-artifact"
    }

    def "stores different versions"() {
        setup:
        def registry = new VersionCatalogDependencyRegistry(true)

        when:
        registry.registerLibrary("group:artifact", "1.1")
        registry.registerLibrary("group:artifact", "1.2")

        then:
        registry.versions.size() == 2
        registry.libraries.size() == 2
        registry.versions[0].alias == "group-artifact"
        registry.versions[1].alias == "group-artifact-x1"
        registry.versions[0].version == "1.1"
        registry.versions[1].version == "1.2"
        registry.libraries[0].alias == "group-artifact"
        registry.libraries[1].alias == "group-artifact-x1"
        registry.libraries[0].versionRef == "group-artifact"
        registry.libraries[1].versionRef == "group-artifact-x1"
    }

    @Unroll
    def "deals with special aliases"() {
        expect:
        new VersionCatalogDependencyRegistry(true).registerLibrary(module, "42") == alias

        where:
        module || alias
        "bundles:a" | "libs.mybundles.a"
        "bundles-a:a" | "libs.mybundles.a.a"
        "versions:b" | "libs.myversions.b"
        "versions-b:b" | "libs.myversions.b.b"
        "plugins:c" | "libs.myplugins.c"
        "plugins-c:c" | "libs.myplugins.c.c"
        "some:extensions" | "libs.some.myextensions"
        "another:class" | "libs.another.myclass"
        "the:convention" | "libs.the.myconvention"
        "some.class.embedded:d" | "libs.some.myclass.embedded.d"
        "Capitalized:Library" | "libs.capitalized.library"
        "UPPERCASE:ALL-THE-THINGS" | "libs.uppercase.all.the.things"
        "embedded-42:numbers" | "libs.embedded.v42.numbers"
    }

    @Unroll
    def "fully qualified or artifact only libraries"() {
        expect:
        new VersionCatalogDependencyRegistry(fullyQualify).registerLibrary(module, "42") == alias

        where:
        fullyQualify | module || alias
        true | "bundles:a" | "libs.mybundles.a"
        false | "bundles:a" | "libs.a"
        true | "com.example.group:artifact" | "libs.com.example.group.artifact"
        false | "com.example.group:artifact" | "libs.artifact"
    }

    @Unroll
    def "fully qualified or shortened plugins"() {
        expect:
        new VersionCatalogDependencyRegistry(fullyQualify).registerPlugin(module, "42") == alias

        where:
        fullyQualify | module || alias
        true | "bundles.a" | "libs.plugins.bundles.a"
        false | "bundles.a" | "libs.plugins.a"
        true | "com.example.group.artifact" | "libs.plugins.com.example.group.artifact"
        false | "com.example.group.artifact" | "libs.plugins.artifact"
    }
}
