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
        store.write({it.writeInt(10)} as BinaryStore.WriteAction)
        store.write({it.writeUTF("x")} as BinaryStore.WriteAction)
        def data1 = store.done()
        store.write({it.writeUTF("y")} as BinaryStore.WriteAction)
        def data2 = store.done()

        then:
        data1.read({it.readInt()} as BinaryStore.ReadAction) == 10
        data1.read({it.readUTF()} as BinaryStore.ReadAction) == "x"
        data1.done()

        data2.read({it.readUTF()} as BinaryStore.ReadAction) == "y"
        data2.done()

        cleanup:
        store.close()
       }

    def "may be empty"() {
        def store = new DefaultBinaryStore(temp.file("foo.bin"))

        when:
        def data = store.done()

        then:
        data.done()
    }
}
