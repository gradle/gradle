/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.internal.file

import groovy.transform.CompileStatic
import org.gradle.api.Action
import org.gradle.api.file.SyncSpec
import org.gradle.api.model.ObjectFactory
import spock.lang.Specification

class DefaultFileSystemOperationsTest extends Specification {

    private FileOperations fileOperations = Mock()
    private ObjectFactory objectFactory = Mock()
    private DefaultFileSystemOperations fileSystemOperations = new DefaultFileSystemOperations(objectFactory, fileOperations)

    def 'copySpec forwards to FileOperations::copySpec'() {
        when:
        fileSystemOperations.copySpec()

        then:
        1 * fileOperations.copySpec()
    }

    @CompileStatic
    def 'sync uses syncSpec'() {
        setup:
        Action<SyncSpec> action = { SyncSpec s ->
            s.preserve {
                it.include('**/*.java')
            }
        }

        when:
        fileSystemOperations.sync(action)

        then:
        1 * fileOperations.sync(action)
    }
}
