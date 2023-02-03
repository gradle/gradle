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

package org.gradle.internal.watch

import com.gradle.enterprise.testing.annotations.LocalOnly
import org.gradle.initialization.StartParameterBuildOptions
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.FileSystemWatchingFixture
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import spock.lang.Requires

@LocalOnly
@Requires({ GradleContextualExecuter.noDaemon })
class NoDaemonFilesystemWatchingIntegrationTest extends AbstractIntegrationSpec implements FileSystemWatchingFixture {

    def "is disabled by default for --no-daemon"() {
        buildFile << """
            apply plugin: "java"
        """

        when:
        run("assemble", "--info", "--${StartParameterBuildOptions.WatchFileSystemOption.LONG_OPTION}")
        then:
        outputContains("Watching the file system is not supported.")
    }
}
