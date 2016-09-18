/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.changedetection.changes

import org.gradle.api.file.FileCollection
import spock.lang.Specification
import spock.lang.Subject

@Subject(NoHistoryArtifactState)
class NoHistoryArtifactStateTest extends Specification {

    def 'outputFiles singleton works as expected'() {
        when: 'a NoHistoryArtifactState is created'
        NoHistoryArtifactState instance = new NoHistoryArtifactState()

        then: 'retrieving its outputFiles returns a value'
        FileCollection outputFiles = instance.outputFiles
        outputFiles != null

        and: 'deserialized outputFiles all point to the same instance'
        outputFiles.is(writeAndRead(outputFiles))

        and: 'deserialized outputFiles.files Set<File> points to the same instance'
        Set<File> fileSet = outputFiles.files
        fileSet.is(writeAndRead(fileSet))

        and: 'that instance is actually the immutable Collections.emptySet() singleton'
        fileSet.is(Collections.emptySet())
    }

    private static <T> T writeAndRead(T instance) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream()
        ObjectOutputStream oos = new ObjectOutputStream(bos)
        oos.writeObject(instance)
        oos.close()
        ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray())
        ObjectInputStream ois = new ObjectInputStream(bis)
        return (T) ois.readObject()
    }
}
