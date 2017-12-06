/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.tasks.compile

import org.gradle.incap.ProcessorType;
import org.gradle.internal.serialize.InputStreamBackedDecoder
import org.gradle.internal.serialize.OutputStreamBackedEncoder
import spock.lang.Specification
import spock.lang.Subject

class AnnotationProcessorInfoTest extends Specification {

    @Subject serializer = AnnotationProcessorInfo.SERIALIZER

    def "serializes"() {
        def data = new AnnotationProcessorInfo()
        data.setProcessor(true)
        data.setIncapSupportType(ProcessorType.SIMPLE)
        data.setName("A")
        def os = new ByteArrayOutputStream()
        def e = new OutputStreamBackedEncoder(os)

        when:
        serializer.write(e, data)
        AnnotationProcessorInfo info = serializer.read(new InputStreamBackedDecoder(new ByteArrayInputStream(os.toByteArray())))

        then:
        data.isProcessor == info.isProcessor
        data.incapSupportType == info.incapSupportType
        data.name == info.name
    }
}
