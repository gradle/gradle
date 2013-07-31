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

package org.gradle.api.internal.tasks.testing.junit.result

import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import org.gradle.api.Action
import org.gradle.internal.id.CompositeIdGenerator
import org.gradle.internal.id.LongIdGenerator
import spock.lang.Specification

import java.nio.charset.Charset

import static org.gradle.api.internal.tasks.testing.junit.result.KryoSerializationUtil.*

class KryoSerializationUtilTest extends Specification {

    Input input
    def utf8 = Charset.forName("utf8")

    void write(Action<Output> action) {
        def baos = new ByteArrayOutputStream()
        def output = new Output(baos)
        action.execute(output)
        output.close()

        input = new Input(new ByteArrayInputStream(baos.toByteArray()))
    }

    def "can write then read string"() {
        when:
        write({
            writeString("a∑´c", utf8, it)
            writeString("123", utf8, it)
            writeString("abc", utf8, it)
        } as Action<Output>)

        then:
        readString(utf8, input) == "a∑´c"
        skipNext(input)
        readString(utf8, input) == "abc"
    }


}
