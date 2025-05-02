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
package org.gradle.api.internal.tasks

import org.gradle.api.internal.CollectionCallbackActionDecorator
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.tasks.SourceSet
import org.gradle.util.TestUtil
import spock.lang.Specification

class DefaultSourceSetContainerTest extends Specification {

    def "can create a source set"() {
        given:
        def container = new DefaultSourceSetContainer(TestFiles.resolver(), TestFiles.taskDependencyFactory(), TestFiles.fileCollectionFactory(), TestUtil.instantiatorFactory().decorateLenient(), TestUtil.objectFactory(), CollectionCallbackActionDecorator.NOOP)

        when:
        SourceSet set = container.create("main")

        then:
        set instanceof DefaultSourceSet
        set.name == "main"
    }
}
