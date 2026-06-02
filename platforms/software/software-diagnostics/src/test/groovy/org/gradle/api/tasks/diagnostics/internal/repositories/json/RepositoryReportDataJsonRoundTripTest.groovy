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
package org.gradle.api.tasks.diagnostics.internal.repositories.json

import org.gradle.api.tasks.diagnostics.internal.repositories.model.ReportContentFilter
import org.gradle.api.tasks.diagnostics.internal.repositories.model.ReportRepository
import org.gradle.api.tasks.diagnostics.internal.repositories.model.RepositoryDeclarationSite
import org.gradle.api.tasks.diagnostics.internal.repositories.model.RepositoryReportProjectModel
import org.gradle.api.tasks.diagnostics.internal.repositories.model.RepositoryRole
import org.gradle.api.tasks.diagnostics.internal.repositories.model.RepositoryType
import org.gradle.util.Path
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

import static org.gradle.api.tasks.diagnostics.internal.repositories.model.RepositoryDeclarationSite.Scope.PROJECT

class RepositoryReportDataJsonRoundTripTest extends Specification {
    @Rule
    TemporaryFolder tempDir = new TemporaryFolder()

    def "round-trips an empty project model"() {
        given:
        def model = new RepositoryReportProjectModel(Path.path(":empty"), "empty", [], [])
        def file = tempDir.newFile("data.json")

        when:
        RepositoryReportDataJsonWriter.write(model, file)
        def readBack = RepositoryReportDataJsonReader.read(file)

        then:
        readBack.projectPath == Path.path(":empty")
        readBack.projectName == "empty"
        readBack.buildscriptRepositories.isEmpty()
        readBack.projectRepositories.isEmpty()
    }

    def "round-trips a Maven repo with full content filter"() {
        given:
        def filter = new ReportContentFilter(
            ['includeGroup("com.example")'],
            ['excludeModuleByRegex("a", "b")'],
            ['compile'] as Set,
            [] as Set,
            ['org.gradle.usage': ['java-api'] as Set]
        )
        def repo = new ReportRepository(
            "MavenRepo",
            RepositoryType.MAVEN,
            "https://repo.maven.apache.org/maven2/",
            true,
            ['BasicAuthentication'],
            true,
            filter,
            [RepositoryRole.PROJECT_DEPENDENCIES] as Set,
            new RepositoryDeclarationSite(PROJECT, Path.path(":app"), "repositories")
        )
        def model = new RepositoryReportProjectModel(Path.path(":app"), "app", [], [repo])
        def file = tempDir.newFile("data.json")

        when:
        RepositoryReportDataJsonWriter.write(model, file)
        def readBack = RepositoryReportDataJsonReader.read(file)

        then:
        readBack.projectRepositories.size() == 1
        def r = readBack.projectRepositories[0]
        r.name == "MavenRepo"
        r.type == RepositoryType.MAVEN
        r.location == "https://repo.maven.apache.org/maven2/"
        r.secure
        r.authSchemes == ['BasicAuthentication']
        r.hasCredentials
        r.contentFilter.includeRules == ['includeGroup("com.example")']
        r.contentFilter.excludeRules == ['excludeModuleByRegex("a", "b")']
        r.contentFilter.onlyForConfigurations == ['compile'] as Set
        r.contentFilter.notForConfigurations == [] as Set
        r.contentFilter.onlyForAttributes == ['org.gradle.usage': ['java-api'] as Set]
        r.roles == [RepositoryRole.PROJECT_DEPENDENCIES] as Set
        r.declarationSite.scope == PROJECT
        r.declarationSite.projectPath == Path.path(":app")
        r.declarationSite.block == "repositories"
    }

    def "round-trips all repository types"() {
        given:
        def repos = [
            buildRepo(RepositoryType.MAVEN_LOCAL, "file:///home/user/.m2/repository"),
            buildRepo(RepositoryType.IVY, "https://example.com/ivy/"),
            buildRepo(RepositoryType.FLAT_DIR, "dirs:[/tmp/libs]"),
            buildRepo(RepositoryType.CUSTOM, "com.example.MyRepository"),
        ]
        def model = new RepositoryReportProjectModel(Path.path(":multi"), "multi", [], repos)
        def file = tempDir.newFile("data.json")

        when:
        RepositoryReportDataJsonWriter.write(model, file)
        def readBack = RepositoryReportDataJsonReader.read(file)

        then:
        readBack.projectRepositories*.type == [RepositoryType.MAVEN_LOCAL, RepositoryType.IVY, RepositoryType.FLAT_DIR, RepositoryType.CUSTOM]
        readBack.projectRepositories*.location == ["file:///home/user/.m2/repository", "https://example.com/ivy/", "dirs:[/tmp/libs]", "com.example.MyRepository"]
    }

    def "round-trips <NO_URL> location"() {
        given:
        def repo = buildRepo(RepositoryType.MAVEN, "<NO_URL>")
        def model = new RepositoryReportProjectModel(Path.path(":nul"), "nul", [], [repo])
        def file = tempDir.newFile("data.json")

        when:
        RepositoryReportDataJsonWriter.write(model, file)
        def readBack = RepositoryReportDataJsonReader.read(file)

        then:
        readBack.projectRepositories[0].location == "<NO_URL>"
    }

    def "round-trips secure=false and hasCredentials=false flags"() {
        given:
        def insecureRepo = new ReportRepository(
            "insecure",
            RepositoryType.MAVEN,
            "http://example.com/legacy",
            false,                           // secure = false
            [],
            false,                           // hasCredentials = false
            ReportContentFilter.EMPTY,
            [RepositoryRole.PROJECT_DEPENDENCIES] as Set,
            new RepositoryDeclarationSite(PROJECT, Path.path(":x"), "repositories")
        )
        def model = new RepositoryReportProjectModel(Path.path(":x"), "x", [], [insecureRepo])
        def file = tempDir.newFile("data.json")

        when:
        RepositoryReportDataJsonWriter.write(model, file)
        def readBack = RepositoryReportDataJsonReader.read(file)

        then:
        def r = readBack.projectRepositories[0]
        !r.secure
        !r.hasCredentials
        r.location == "http://example.com/legacy"
    }

    private static ReportRepository buildRepo(RepositoryType type, String location) {
        new ReportRepository(
            "name", type, location, true, [], false,
            ReportContentFilter.EMPTY,
            [RepositoryRole.PROJECT_DEPENDENCIES] as Set,
            new RepositoryDeclarationSite(PROJECT, Path.path(":x"), "repositories")
        )
    }
}
