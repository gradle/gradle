/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.catalog

import org.gradle.internal.build.BuildIdentity
import org.gradle.internal.operations.ProgressDisplayName
import org.gradle.internal.operations.TestBuildOperationRunner
import org.gradle.util.Path
import spock.lang.Issue
import spock.lang.Specification

@Issue("https://github.com/gradle/gradle/issues/39539")
class DefaultDependenciesAccessorsTest extends Specification {

    def buildOperationRunner = new TestBuildOperationRunner()

    def "shows the generation of the dependency accessors of the #build on a worker line"() {
        when:
        buildOperationRunner.run(DefaultDependenciesAccessors.dependencyAccessorsOperation(new BuildIdentity(buildPath), "libs", {}))

        then:
        def generate = buildOperationRunner.log.descriptors.last()
        generate.displayName == displayName
        generate.progressDisplayName == ProgressDisplayName.of(ownerToShow, "Generating dependency accessors for 'libs'")

        where:
        build            | buildPath              | ownerToShow | displayName
        "root build"     | Path.ROOT              | null        | "Generate dependency accessors for 'libs'"
        "included build" | Path.path(":included") | ":included" | "Generate dependency accessors for 'libs' (:included)"
    }

    def "shows the generation of the project accessors of the #build on a worker line"() {
        when:
        buildOperationRunner.run(DefaultDependenciesAccessors.projectAccessorsOperation(new BuildIdentity(buildPath), {}))

        then:
        def generate = buildOperationRunner.log.descriptors.last()
        generate.displayName == displayName
        generate.progressDisplayName == ProgressDisplayName.of(ownerToShow, "Generating project accessors")

        where:
        build            | buildPath              | ownerToShow | displayName
        "root build"     | Path.ROOT              | null        | "Generate project accessors"
        "included build" | Path.path(":included") | ":included" | "Generate project accessors (:included)"
    }
}
