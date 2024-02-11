/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.cache.internal.streams

import org.gradle.internal.serialize.BaseSerializerFactory
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Serializer
import org.gradle.internal.serialize.kryo.KryoBackedDecoder
import org.gradle.internal.serialize.kryo.KryoBackedEncoder
import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule

import java.util.concurrent.ConcurrentHashMap

class DefaultValueStoreTest extends ConcurrentSpec {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(DefaultValueStoreTest.class)
    def dir = tmpDir.file("files")
    def store = store()

    def cleanup() {
        store?.close()
    }

    def "can write and then read a block multiple times"() {
        expect:
        def block = write("test")
        read(block) == "test"
        read(block) == "test"
        read(block) == "test"
    }

    def "can write multiple blocks and read in any order"() {
        expect:
        def block1 = write("test 1")
        def block2 = write("test 2")
        def block3 = write("test 3")

        read(block2) == "test 2"
        read(block3) == "test 3"
        read(block1) == "test 1"
    }

    def "can write blocks and read block from multiple threads"() {
        expect:
        def blocks = new ConcurrentHashMap<Integer, BlockAddress>()
        async {
            10.times { index ->
                start {
                    blocks[index] = write("test $index")
                }
            }
        }
        blocks.size() == 10
        async {
            10.times { index ->
                start {
                    assert read(blocks[index]) == "test $index"
                }
            }
        }
    }

    def "can write block and read from multiple threads"() {
        expect:
        def block1 = write("test 1")
        def block2 = write("test 2")
        async {
            10.times { index ->
                assert read(block1) == "test 1"
                assert read(block2) == "test 2"
            }
        }
    }

    def "can persist block address"() {
        expect:
        def block1 = write("test 1")
        def block2 = write("test 2")

        def copy1 = storeAndLoad(block1)
        def copy2 = storeAndLoad(block2)

        read(copy2) == "test 2"
        read(copy1) == "test 1"
    }

    def "can persist block address and read block later"() {
        expect:
        def block1 = write("test 1")
        def block2 = write("test 2")

        def copy1 = storeAndLoad(block1)
        def copy2 = storeAndLoad(block2)

        def dest = store()
        dest.read(copy2) == "test 2"
        dest.read(copy1) == "test 1"

        cleanup:
        dest?.close()
    }

    def "does not overwrite blocks written by previous instances"() {
        expect:
        def block1 = write("test 1")
        def block2 = write("test 2")
        store.close()

        def second = store()
        def block3 = second.write("test 3")
        second.read(block2) == "test 2"
        second.read(block1) == "test 1"
        second.read(block3) == "test 3"

        cleanup:
        second?.close()
    }

    def "can write empty block"() {
        def serializer = Stub(Serializer)
        _ * serializer.write(_, _)
        _ * serializer.read(_) >> { Decoder decoder ->
            assert decoder.inputStream.read(new byte[10]) == -1
            "result"
        }
        def store = DefaultValueStore.encoding(dir, "values", serializer)

        expect:
        def block = store.write("something")
        store.read(block) == "result"

        cleanup:
        store?.close()
    }

    ValueStore<String> store() {
        return DefaultValueStore.encoding(dir, "values", BaseSerializerFactory.STRING_SERIALIZER)
    }

    BlockAddress write(String text) {
        return store.write(text)
    }

    String read(BlockAddress block) {
        return store.read(block)
    }

    BlockAddress storeAndLoad(BlockAddress block) {
        def outstr = new ByteArrayOutputStream()
        def encoder = new KryoBackedEncoder(outstr)
        def serializer = new BlockAddressSerializer()
        serializer.write(encoder, block)
        encoder.flush()
        return serializer.read(new KryoBackedDecoder(new ByteArrayInputStream(outstr.toByteArray())))
    }
}
