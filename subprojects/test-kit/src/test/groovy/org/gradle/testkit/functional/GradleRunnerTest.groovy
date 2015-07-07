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

package org.gradle.testkit.functional

import org.gradle.testkit.functional.internal.DefaultGradleRunner
import org.gradle.testkit.functional.internal.dist.GradleDistribution
import org.gradle.testkit.functional.internal.dist.InstalledGradleDistribution
import spock.lang.Specification
import spock.lang.Unroll

class GradleRunnerTest extends Specification {
    def "can create instance for default Gradle distribution"() {
        when:
        GradleRunner gradleRunner = GradleRunner.create()

        then:
        gradleRunner instanceof DefaultGradleRunner
    }

    @Unroll
    def "can create instance with supported Gradle distribution type"() {
        when:
        GradleRunner gradleRunner = GradleRunner.create(new InstalledGradleDistribution(new File('some/dir')))

        then:
        gradleRunner instanceof DefaultGradleRunner
    }

    def "throws exception for unsupported Gradle distribution type"() {
        when:
        GradleRunner.create(new MyGradleDistribution())

        then:
        Throwable t = thrown(IllegalArgumentException)
        t.message == "Invalid Gradle distribution type: ${MyGradleDistribution.name}"
    }

    private class MyGradleDistribution implements GradleDistribution {
        String getDisplayName() {
            'My custom Gradle distribution'
        }
    }
}
