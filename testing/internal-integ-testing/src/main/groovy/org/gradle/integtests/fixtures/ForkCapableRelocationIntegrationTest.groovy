/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.integtests.fixtures

import org.gradle.test.fixtures.file.TestFile


abstract class ForkCapableRelocationIntegrationTest extends AbstractProjectRelocationIntegrationTest {

    abstract String getDaemonConfiguration()

    abstract String getForkOptionsObject()

    def "can provide relocatable command line arguments to forked daemons"() {
        def originalDir = file("original-dir")
        originalDir.file("settings.gradle") << localCacheConfiguration()
        setupProjectWithJavaAgentIn(originalDir)

        def relocatedDir = file("relocated-dir")
        relocatedDir.file("settings.gradle") << localCacheConfiguration()
        setupProjectWithJavaAgentIn(relocatedDir)

        when:
        inDirectory(originalDir)
        withBuildCache().run taskName

        then:
        executedAndNotSkipped taskName

        and:
        outputContains('JavaAgent configured!')

        when:
        inDirectory(originalDir)
        withBuildCache().run taskName

        then:
        skipped taskName

        when:
        inDirectory(relocatedDir)
        withBuildCache().run taskName

        then:
        skipped taskName
    }

    void setupProjectWithJavaAgentIn(TestFile directory) {
        setupProjectIn(directory)
        def javaAgent = new JavaAgentFixture()
        javaAgent.writeProjectTo(directory)

        directory.file('build.gradle') << """
            // Use a daemon to build things
            ${daemonConfiguration}
            ${javaAgent.useJavaAgent(forkOptionsObject)}
        """
    }
}
