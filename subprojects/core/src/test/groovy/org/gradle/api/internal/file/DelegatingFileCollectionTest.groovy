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
package org.gradle.api.internal.file

import org.gradle.api.file.FileCollection
import org.gradle.api.internal.file.collections.DelegatingFileCollection
import org.gradle.api.internal.file.collections.MinimalFileSet
import org.gradle.api.specs.Spec
import org.gradle.api.specs.Specs

import spock.lang.Specification

class DelegatingFileCollectionTest extends Specification {
    FileCollection delegatedTo = Mock()
    DelegatingFileCollection fileCollection = new DelegatingFileCollection() {
        @Override
        FileCollection getDelegate() {
            delegatedTo
        }
    }

    File aFile = new File("foo")
    FileCollection aCollection = Stub()
    Object anObject = new Object()

    def "delegates all method calls"() {
        when:
        fileCollection.with {
            getSingleFile()
            getFiles()
            contains(aFile)
            getAsPath()
            plus(aCollection)
            minus(aCollection)
            filter({ true })
            filter(Specs.satisfyAll())
            delegate.asType(List) // avoid collision with DGM method
            add(aCollection)
            isEmpty()
            stopExecutionIfEmpty()
            getAsFileTree()
            addToAntBuilder(anObject, "nodeName", FileCollection.AntType.MatchingTask)
            addToAntBuilder(anObject, "nodeName")
            getBuildDependencies()
            delegate.iterator() // avoid collision with DGM method
        }

        then:
        with(delegatedTo) {
            1 * getSingleFile()
            1 * getFiles()
            1 * contains({ it.is(aFile) })
            1 * getAsPath()
            1 * plus({ it.is(aCollection) })
            1 * minus({ it.is(aCollection) })
            1 * filter(_ as Closure)
            1 * filter(_ as Spec)
            1 * asType(List)
            1 * add({ it.is(aCollection) })
            1 * isEmpty()
            1 * stopExecutionIfEmpty()
            1 * getAsFileTree()
            1 * addToAntBuilder(anObject, "nodeName", FileCollection.AntType.MatchingTask)
            1 * addToAntBuilder(anObject, "nodeName")
            1 * getBuildDependencies()
            1 * iterator()
            0 * _
        }
    }

    interface MyFileCollection extends FileCollection, MinimalFileSet {}

    def "delegates getDisplayName() to toString() if delegate is not a MinimalFileSet"() {
        when:
        fileCollection.getDisplayName()

        then:
        1 * delegatedTo.toString()

    }

    def "delegates getDisplayName() to getDisplayName() if delegate is a MinimalFileSet"() {
        delegatedTo = Mock(MyFileCollection)

        when:
        fileCollection.getDisplayName()

        then:
        1 * delegatedTo.getDisplayName()
    }
}
