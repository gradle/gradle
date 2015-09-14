/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.play.integtest

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.play.integtest.fixtures.app.BasicPlayApp
import org.gradle.play.integtest.fixtures.PlayApp
import org.gradle.play.internal.DefaultPlayPlatform
import org.gradle.test.fixtures.archive.JarTestFixture
import org.gradle.test.fixtures.archive.ZipTestFixture
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Unroll

public class PlayPlatformIntegrationTest extends AbstractIntegrationSpec {
    PlayApp playApp = new BasicPlayApp()

    def setup() {
        playApp.writeSources(testDirectory)
    }

    def "can build play app binary for default platform"() {
        when:
        succeeds("stage")

        then:
        file("build/stage/playBinary/lib").assertContainsDescendants(
            "play_2.11-${DefaultPlayPlatform.DEFAULT_PLAY_VERSION}.jar"
        )
    }

    @Unroll
    def "can build play app binary for specified platform [#platform]"() {
        when:
        buildFile << """
model {
    components {
        play {
            platform ${platform}
        }
    }
}
"""

        succeeds("stage")

        then:
        file("build/stage/playBinary/lib").assertContainsDescendants(
                "play_${scalaPlatform}-${playVersion}.jar"
        )

        where:
        platform                       | playVersion | scalaPlatform
        "play: '2.2.4'"                | '2.2.4'     | '2.10'
        "play: '2.2.4', scala: '2.10'" | '2.2.4'     | '2.10'

        "play: '2.3.6'"                | '2.3.6'     | '2.11'
        "play: '2.3.6', scala: '2.10'" | '2.3.6'     | '2.10'
        "play: '2.3.6', scala: '2.11'" | '2.3.6'     | '2.11'

        "play: '2.3.8'"                | '2.3.8' | '2.11'
        "play: '2.3.8', scala: '2.10'" | '2.3.8' | '2.10'
        "play: '2.3.8', scala: '2.11'" | '2.3.8' | '2.11'

    }

    @Requires(TestPrecondition.JDK8_OR_LATER)
    @Unroll
    def "can build play app binary for specified platform on JDK8 [#platform]"() {
        when:
        buildFile << """
model {
    components {
        play {
            platform ${platform}
        }
    }
}
"""

        succeeds("stage")

        then:
        file("build/stage/playBinary/lib").assertContainsDescendants(
            "play_${scalaPlatform}-${playVersion}.jar"
        )

        where:
        platform                       | playVersion | scalaPlatform
        "play: '2.4.0'"                | '2.4.0'     | '2.11'
        "play: '2.4.0', scala: '2.10'" | '2.4.0'     | '2.10'
        "play: '2.4.0', scala: '2.11'" | '2.4.0'     | '2.11'
    }


    def "fails when trying to build a Play 2.2.x application with Scala 2.11.x"() {
        when:
        buildFile << """
model {
    components {
        play {
            platform play: '2.2.4', scala: '2.11'
        }
    }
}
"""

        then:
        fails "assemble"

        and:
        failure.assertHasCause "Play versions 2.2.x are not compatible with Scala platform 2.11. Compatible Scala platforms are [2.10]."
    }

    def "fails when trying to build for an unsupported play platform"() {
        when:
        buildFile << """
model {
    components {
        play {
            platform play: '2.1.0'
        }
    }
}
"""

        then:
        fails "assemble"

        and:
        failure.assertHasCause "Not a supported Play version: 2.1.0. This plugin is compatible with: [2.4.x, 2.3.x, 2.2.x]."
    }

    def "fails when trying to build for an invalid scala platform"() {
        when:
        buildFile << """
model {
    components {
        play {
            platform play: '2.3.6', scala: 'X'
        }
    }
}
"""

        then:
        fails "assemble"

        and:
        failure.assertHasCause "Not a supported Scala platform identifier X. Supported values are: ['2.10', '2.11']."
    }

    def "fails when trying to build for multiple play platforms"() {
        when:
        buildFile << """
model {
    components {
        play {
            platform play: '2.2.4'
            platform play: '2.3.6'
        }
    }
}
"""
        then:
        fails "assemble"

        and:
        failure.assertHasCause "Multiple target platforms for 'PlayApplicationSpec' is not (yet) supported."
    }

    JarTestFixture jar(String fileName) {
        new JarTestFixture(file(fileName))
    }

    ZipTestFixture zip(String fileName) {
        new ZipTestFixture(file(fileName))
    }
}
