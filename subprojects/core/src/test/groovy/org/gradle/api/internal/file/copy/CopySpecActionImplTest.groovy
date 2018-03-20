/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.api.internal.file.copy

import org.gradle.api.file.FileTree
import org.gradle.api.internal.file.CopyActionProcessingStreamAction
import org.gradle.internal.nativeintegration.filesystem.FileSystem
import org.gradle.internal.reflect.Instantiator
import spock.lang.Specification

class CopySpecActionImplTest extends Specification {
    def "can visit spec source"() {
        given:
        def action = Mock(CopyActionProcessingStreamAction)
        def instantiator = Mock(Instantiator)
        def fileSystem = Mock(FileSystem)
        def source = Mock(FileTree)
        def resolvedSpec = Mock(ResolvedCopySpec)

        def copySpecInternalAction = new CopySpecActionImpl(action, instantiator, fileSystem, false)

        when:
        copySpecInternalAction.execute(resolvedSpec)

        then:
        1 * resolvedSpec.getSource() >> source
        1 * source.visit(_ as CopyFileVisitorImpl)
    }
}
