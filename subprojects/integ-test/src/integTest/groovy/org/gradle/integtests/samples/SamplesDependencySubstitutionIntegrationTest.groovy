/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.integtests.samples

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.Sample
import org.gradle.integtests.fixtures.UsesSample
import org.gradle.util.TextUtil
import org.junit.Rule


class SamplesDependencySubstitutionIntegrationTest extends AbstractIntegrationSpec {
    @Rule public final Sample sample = new Sample(temporaryFolder, 'dependency-substitution')

    @UsesSample("dependency-substitution")
    def "can run sample with all external dependencies" () {
        given:
        inDirectory "dependency-substitution"

        when:
        succeeds "showJarFiles"

        then:
        output.contains("project project1 is external to this build")
        TextUtil.normaliseFileSeparators(output).contains("repo/org.example/project1/1.0/project1-1.0.jar")
        output.contains("project project2 is external to this build")
        TextUtil.normaliseFileSeparators(output).contains("repo/org.example/project2/1.0/project2-1.0.jar")
        output.contains("project project3 is external to this build")
        TextUtil.normaliseFileSeparators(output).contains("repo/org.example/project3/1.0/project3-1.0.jar")
    }

    @UsesSample("dependency-substitution")
    def "can run sample with some internal projects" () {
        given:
        inDirectory "dependency-substitution"

        when:
        args("-DuseLocal=project1,project2")
        succeeds "showJarFiles"

        then:
        output.contains("project project1 is INTERNAL to this build")
        TextUtil.normaliseFileSeparators(output).contains("project1/build/libs/project1-1.0.jar")
        output.contains("project project2 is INTERNAL to this build")
        TextUtil.normaliseFileSeparators(output).contains("project2/build/libs/project2-1.0.jar")
        output.contains("project project3 is external to this build")
        TextUtil.normaliseFileSeparators(output).contains("repo/org.example/project3/1.0/project3-1.0.jar")
    }
}
