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

package org.gradle.internal.watch.vfs.impl

import org.gradle.internal.snapshot.VfsRoot
import org.gradle.internal.snapshot.VfsRootReference
import org.gradle.internal.snapshot.VfsRootReference.VfsUpdateFunction
import spock.lang.Specification

class WatchingNotSupportedFileSystemWatchingHandlerTest extends Specification {
    def vfsRoot = Mock(VfsRoot)
    def rootReference = Stub(VfsRootReference) {
        update(_ as VfsUpdateFunction) >> { VfsUpdateFunction updateFunction ->
            updateFunction.update(vfsRoot)
        }
    }
    def watchingNotSupportedHandler = new WatchingNotSupportedFileSystemWatchingHandler(rootReference)

    def "invalidates the virtual file system before and after the build"() {

        when:
        watchingNotSupportedHandler.afterBuildStarted(retentionEnabled)
        then:
        1 * vfsRoot.invalidateAll()
        0 * _

        when:
        watchingNotSupportedHandler.beforeBuildFinished(retentionEnabled)
        then:
        1 * vfsRoot.invalidateAll()
        0 * _

        where:
        retentionEnabled << [true, false]
    }
}
