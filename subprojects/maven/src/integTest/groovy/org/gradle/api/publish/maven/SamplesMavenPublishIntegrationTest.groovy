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


package org.gradle.api.publish.maven
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.Sample
import org.junit.Rule

public class SamplesMavenPublishIntegrationTest extends AbstractIntegrationSpec {
    @Rule public final Sample quickstart = new Sample("maven-publish/quickstart")
    @Rule public final Sample javaProject = new Sample("maven-publish/javaProject")
    @Rule public final Sample pomCustomization = new Sample("maven-publish/pomCustomization")

    def quickstart() {
        given:
        sample quickstart

        and:
        def fileRepo = maven(quickstart.dir.file("build/repo"))
        def module = fileRepo.module("org.gradle.sample", "quickstart", "1.0")

        when:
        succeeds "publish"

        then:
        def pom = module.parsedPom
        module.assertPublishedAsJavaModule()
        pom.scopes.isEmpty()
    }

    def javaProject() {
        given:
        sample javaProject

        and:
        def fileRepo = maven(javaProject.dir.file("build/repo"))
        def module = fileRepo.module("org.gradle.sample", "javaProject", "1.0")

        when:
        succeeds "publish"

        then:
        module.assertPublished()
        module.assertArtifactsPublished("javaProject-1.0.jar", "javaProject-1.0-sources.jar", "javaProject-1.0.pom")
        module.parsedPom.packaging == null
        module.parsedPom.scopes.runtime.assertDependsOn("commons-collections", "commons-collections", "3.0")
    }

    def pomCustomization() {
        given:
        sample pomCustomization

        and:
        def fileRepo = maven(pomCustomization.dir.file("build/repo"))
        def module = fileRepo.module("org.gradle.sample", "pomCustomization", "1.0")

        when:
        succeeds "publish"

        then:
        def pom = module.parsedPom
        module.assertPublished()
        pom.packaging == "pom"
        pom.description == "A demonstration of maven pom customisation"
    }
}
