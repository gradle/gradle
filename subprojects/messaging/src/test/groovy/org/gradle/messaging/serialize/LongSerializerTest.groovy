package org.gradle.messaging.serialize

import spock.lang.Specification

/**
 * By Szczepan Faber on 5/21/13
 */
class LongSerializerTest extends Specification {

    def serializer = new LongSerializer()

    def "writes and reads Longs"() {
        def bytes = new ByteArrayOutputStream();

        when:
        serializer.write(bytes, 144L)

        then:
        serializer.read(new ByteArrayInputStream(bytes.toByteArray())) == 144L
    }

    def "does not permit null"() {
        when:
        serializer.write(new ByteArrayOutputStream(), null)

        then:
        thrown(IllegalArgumentException)
    }
}
