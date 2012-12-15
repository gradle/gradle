package org.gradle.messaging.remote.internal.hub.protocol

import spock.lang.Specification

class ChannelIdentifierTest extends Specification {
    def "equals and hash code"() {
        def id = new ChannelIdentifier("channel")
        def same = new ChannelIdentifier("channel")
        def different = new ChannelIdentifier("other")

        expect:
        id == same
        id.hashCode() == same.hashCode()
        id != different
        id != null
        id != "channel"
    }
}
