package org.gradle.sample

import spock.lang.Specification

class MessageUtilsTest extends Specification {
    def "test getMessage()"() {
        expect:
        MessageUtils.getMessage() == "Hello,      World!"
    }
}
