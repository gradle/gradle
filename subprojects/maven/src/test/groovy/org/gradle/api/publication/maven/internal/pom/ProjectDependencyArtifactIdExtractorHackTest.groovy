/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.publication.maven.internal.pom

import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependency
import org.gradle.api.plugins.BasePlugin
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import spock.lang.Issue

@Issue("GRADLE-443")
class ProjectDependencyArtifactIdExtractorHackTest extends AbstractProjectBuilderSpec {
    def extractor

    def setup() {
        extractor = new ProjectDependencyArtifactIdExtractorHack(new DefaultProjectDependency(project, null, true))
    }

    def "artifact ID defaults to project name if neither archivesBaseName nor mavenDeployer.pom.artifactId is configured"() {
        expect:
        extractor.extract() == project.name
    }

    def "artifact ID honors archivesBaseName"() {
        project.pluginManager.apply(BasePlugin)
        project.archivesBaseName = "changed"

        expect:
        extractor.extract() == "changed"
    }

}
