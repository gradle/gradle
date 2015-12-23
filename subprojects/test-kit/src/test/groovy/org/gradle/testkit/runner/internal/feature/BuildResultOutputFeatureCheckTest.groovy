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

package org.gradle.testkit.runner.internal.feature

import org.gradle.testkit.runner.UnsupportedFeatureException
import org.gradle.util.GradleVersion
import spock.lang.Specification

class BuildResultOutputFeatureCheckTest extends Specification {

    public static final GradleVersion SUPPORTED_GRADLE_VERSION = BuildResultOutputFeatureCheck.SUPPORTED_VERSION
    public static final GradleVersion UNSUPPORTED_GRADLE_VERSION = GradleVersion.version('2.4')

    def "supported Gradle version passed check"() {
        given:
        BuildResultOutputFeatureCheck featureCheck = new BuildResultOutputFeatureCheck(SUPPORTED_GRADLE_VERSION)

        when:
        featureCheck.verify()

        then:
        noExceptionThrown()
    }

    def "unsupported Gradle version throws exception"() {
        given:
        BuildResultOutputFeatureCheck featureCheck = new BuildResultOutputFeatureCheck(UNSUPPORTED_GRADLE_VERSION)

        when:
        featureCheck.verify()

        then:
        Throwable t = thrown(UnsupportedFeatureException)
        t.message == "The version of Gradle you are using ($UNSUPPORTED_GRADLE_VERSION.version) does not capture build output with the GradleRunner. Support for this is available in Gradle $SUPPORTED_GRADLE_VERSION.version and all later versions."
    }
}
