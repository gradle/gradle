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



package org.gradle.integtests.tooling.r15

import org.gradle.integtests.fixtures.executer.OutputScraper
import org.gradle.integtests.tooling.fixture.MinTargetGradleVersion
import org.gradle.integtests.tooling.fixture.MinToolingApiVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.model.GradleProject

import static java.util.Arrays.asList

@MinToolingApiVersion("1.5")
@MinTargetGradleVersion("1.5")
class ConfigurationOnDemandCrossVersionSpec extends ToolingApiSpecification {

    def setup() {
        file("gradle.properties") << "systemProp.org.gradle.configuration.ondemand=true"
    }

    def "building model evaluates all projects regardless of configuration on demand mode"() {
        given:
        file("settings.gradle") << "include 'api', 'impl', 'other'"
        file("build.gradle") << "description = 'Configure on demand: ' + System.properties['org.gradle.configuration.ondemand']"

        when:
        def out = new ByteArrayOutputStream()
        GradleProject project = withConnection { ProjectConnection it ->
            it.model(GradleProject).setStandardOutput(out).withArguments("-i").get()
        }

        then:
        project.description == 'Configure on demand: true'
        new OutputScraper(out.toString()).assertProjectsEvaluated(asList(":", ":api", ":impl", ":other"));
    }

    def "running tasks takes advantage of configuration on demand"() {
        given:
        file("settings.gradle") << "include 'api', 'impl', 'other'"

        file("build.gradle") << "allprojects { task foo }"
        file("impl/build.gradle") << "task bar(dependsOn: ':api:foo')"
        file("other/build.gradle") << "assert false: 'should not be evaluated'"

        when:
        def out = new ByteArrayOutputStream()
        withConnection { ProjectConnection it ->
            it.newBuild().setStandardOutput(out).withArguments("-i").forTasks(":impl:bar").run()
        }

        then:
        new OutputScraper(out.toString()).assertProjectsEvaluated(asList(":", ":impl", ":api"));
    }
}