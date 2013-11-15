/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.store

import org.gradle.api.internal.cache.BinaryStore
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class DefaultBinaryStoreTest extends Specification {

    @Rule TestNameTestDirectoryProvider temp = new TestNameTestDirectoryProvider()

    def "stores binary data"() {
        def store = new DefaultBinaryStore(temp.file("foo.bin"))

        when:
        store.write({ it.writeInt(10) } as BinaryStore.WriteAction)
        store.write({ it.writeUTF("x") } as BinaryStore.WriteAction)
        def data1 = store.done()
        store.write({ it.writeUTF("y") } as BinaryStore.WriteAction)
        def data2 = store.done()

        then:
        data1.read({ it.readInt() } as BinaryStore.ReadAction) == 10
        data1.read({ it.readUTF() } as BinaryStore.ReadAction) == "x"
        data1.done()

        data2.read({ it.readUTF() } as BinaryStore.ReadAction) == "y"
        data2.done()

        cleanup:
        store.close()
    }

    def "data can be re-read"() {
        def store = new DefaultBinaryStore(temp.file("foo.bin"))

        when:
        store.write({ it.writeInt(10) } as BinaryStore.WriteAction)
        store.write({ it.writeUTF("x") } as BinaryStore.WriteAction)
        def data = store.done()

        then:
        data.read({ it.readInt() } as BinaryStore.ReadAction) == 10
        data.read({ it.readUTF() } as BinaryStore.ReadAction) == "x"
        data.done()

        then:
        data.read({ it.readInt() } as BinaryStore.ReadAction) == 10
        data.read({ it.readUTF() } as BinaryStore.ReadAction) == "x"
        data.done()

        cleanup:
        store.close()
    }

    class SomeException extends RuntimeException {}

    def "write action exception is propagated to the client"() {
        def store = new DefaultBinaryStore(temp.file("foo.bin"))

        when:
        store.write({ throw new SomeException() } as BinaryStore.WriteAction)

        then:
        def e = thrown(Exception)
        e.cause.class == SomeException
    }

    def "read action exception is propagated to the client"() {
        def store = new DefaultBinaryStore(temp.file("foo.bin"))
        store.write({ it.writeInt(10) } as BinaryStore.WriteAction)
        def data = store.done()

        when:
        data.read({ throw new SomeException() } as BinaryStore.ReadAction)

        then:
        def e = thrown(Exception)
        e.cause.class == SomeException
    }

    def "may not read beyond what was written"() {
        def store = new DefaultBinaryStore(temp.file("foo.bin"))

        when:
        store.write({ it.writeInt(10) } as BinaryStore.WriteAction)
        def data1 = store.done()

        store.write({ it.writeInt(20) } as BinaryStore.WriteAction)
        def data2 = store.done()

        then:
        data1.read({ it.readInt() } as BinaryStore.ReadAction) == 10

        when:
        data1.read({ it.readInt() } as BinaryStore.ReadAction)

        then:
        1==1

        cleanup:
        store.close()
    }

    def "may be empty"() {
        def store = new DefaultBinaryStore(temp.file("foo.bin"))

        when:
        def data = store.done()
        store.close()

        then:
        data.done()
    }
}
