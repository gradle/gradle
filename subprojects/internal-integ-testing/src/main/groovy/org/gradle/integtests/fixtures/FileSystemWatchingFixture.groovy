/*
 * Copyright 2020 the original author or authors.
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

import groovy.transform.SelfType
import org.junit.Before

@SelfType(AbstractIntegrationSpec)
trait FileSystemWatchingFixture {

    @Before
    void setupFirstBuild() {
        // Make the first build in each test drop the VFS state
        executer.withArgument(FileSystemWatchingHelper.dropVfsArgument)
    }

    AbstractIntegrationSpec withWatchFs() {
        executer.withArgument(FileSystemWatchingHelper.enableFsWatchingArgument)
        this
    }

    AbstractIntegrationSpec withoutWatchFs() {
        executer.withArgument(FileSystemWatchingHelper.disableFsWatchingArgument)
        this
    }

    void waitForChangesToBePickedUp() {
        FileSystemWatchingHelper.waitForChangesToBePickedUp()
    }

    int getReceivedFileSystemEventsInCurrentBuild() {
        def duringBuildStatusLine = result.getPostBuildOutputLineThatContains(" file system events for current build")
        def numberMatcher = duringBuildStatusLine =~ /Received (\d+) file system events for current build/
        return numberMatcher[0][1] as int
    }
}
