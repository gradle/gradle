package org.gradle.api.internal.artifacts.ivyservice.resolveengine.store

import org.gradle.api.internal.cache.BinaryStore
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class DefaultBinaryStoreTest extends Specification {

    @Rule TestNameTestDirectoryProvider temp = new TestNameTestDirectoryProvider()

    def "stores binary data"() {
        def store = new DefaultBinaryStore(temp.file("foo.bin"))
        store.write({it.writeInt(10)} as BinaryStore.WriteAction)
        store.write({it.writeUTF("x")} as BinaryStore.WriteAction)

        when:
        def data = store.done()

        then:
        data.read({it.readInt()} as BinaryStore.ReadAction) == 10
        data.read({it.readUTF()} as BinaryStore.ReadAction) == "x"
        data.done()
    }

    def "may be empty"() {
        def store = new DefaultBinaryStore(temp.file("foo.bin"))

        when:
        def data = store.done()

        then:
        data.done()
    }
}
