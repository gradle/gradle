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

package org.gradle.testkit.runner.internal

import org.gradle.testkit.runner.*
import spock.lang.Specification
import spock.lang.Unroll

class GradleRunnerTest extends Specification {

    @Unroll
    def "can create instance with supported Gradle distribution type #gradleDistribution.getClass().getName()"() {
        when:
        GradleRunner gradleRunner = GradleRunner.create(gradleDistribution)

        then:
        noExceptionThrown()
        gradleRunner

        where:
        gradleDistribution << [new VersionBasedGradleDistribution('2.8'),
                               new InstalledGradleDistribution(new File('some/dir')),
                               new URILocatedGradleDistribution(new URI('http://services.gradle.org/distributions/gradle-2.8-bin.zip'))]
    }

    def "throws exception for unsupported Gradle distribution type"() {
        when:
        GradleRunner.create(new MyGradleDistribution())

        then:
        Throwable t = thrown(IllegalArgumentException)
        t.message == "Invalid Gradle distribution type: ${MyGradleDistribution.name}"
    }

    private class MyGradleDistribution implements GradleDistribution<String> {
        String getHandle() {
            '2.8'
        }
    }
}
