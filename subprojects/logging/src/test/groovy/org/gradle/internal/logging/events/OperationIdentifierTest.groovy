package org.gradle.internal.logging.events

import spock.lang.Specification

class OperationIdentifierTest extends Specification {
    def "allows instantiation with non-zero values"() {
        expect:
        new OperationIdentifier(-1).getId() == -1
        new OperationIdentifier(1).getId() == 1
        new OperationIdentifier(Long.MAX_VALUE).getId() == Long.MAX_VALUE
        new OperationIdentifier(Long.MIN_VALUE).getId() == Long.MIN_VALUE
    }

    def "disallows instantiation with a value of 0"() {
        when:
        new OperationIdentifier(0)

        then:
        thrown(IllegalArgumentException)
    }
}
