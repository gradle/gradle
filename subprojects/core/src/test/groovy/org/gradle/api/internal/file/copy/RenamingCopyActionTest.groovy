/*
 * Copyright 2009 the original author or authors.
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

import org.gradle.api.Transformer
import org.gradle.api.file.FileCopyDetails
import org.gradle.api.file.RelativePath
import spock.lang.Specification

class RenamingCopyActionTest extends Specification {

    Transformer transformer = Mock()
    FileCopyDetails details = Mock()
    RenamingCopyAction action

    def setup() {
        action = new RenamingCopyAction(transformer)
        details.getRelativePath() >> new RelativePath(true, "a", "b")
    }

    def 'transforms last segment of path with non-null input'() {
        when:
        action.execute(details);

        then:
        1 * transformer.transform('b') >> 'c'
        1 * details.setRelativePath(new RelativePath(true, "a", "c"))
    }

    def 'does not transform last segment of path with null input'() {
        when:
        action.execute(details);

        then:
        1 * transformer.transform('b') >> null
        0 * details.setRelativePath(new RelativePath(true, "a", "b"))
    }
}
