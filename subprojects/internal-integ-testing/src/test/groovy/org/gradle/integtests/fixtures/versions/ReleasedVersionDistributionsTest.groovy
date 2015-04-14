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

package org.gradle.integtests.fixtures.versions

import org.gradle.internal.Factory
import spock.lang.Specification

import static org.gradle.util.GradleVersion.version

class ReleasedVersionDistributionsTest extends Specification {

    Properties props = new Properties()

    def versions() {
        new ReleasedVersionDistributions(new Factory<Properties>() {
            Properties create() {
                props
            }
        })
    }

    // Will fail if the classpath resource is not available, see ClasspathVersionSource
    def "can create from classpath"() {
        when:
        def versions = new ReleasedVersionDistributions()

        then:
        !versions.all.empty
        versions.all*.version == versions.all*.version.sort().reverse()
    }

    def "get most recent final does that"() {
        when:
        props.mostRecent = "1.2"

        then:
        versions().mostRecentFinalRelease.version == version("1.2")
    }

    def "get most recent snapshot does that"() {
        when:
        props.mostRecentSnapshot = "2.5-20150413220018+0000"

        then:
        versions().mostRecentSnapshot.version == version("2.5-20150413220018+0000")
    }

    def "get all final does that"() {
        when:
        props.versions = "1.3-rc-1 1.2"

        then:
        versions().all*.version == [version("1.3-rc-1"), version("1.2")]
    }

}
