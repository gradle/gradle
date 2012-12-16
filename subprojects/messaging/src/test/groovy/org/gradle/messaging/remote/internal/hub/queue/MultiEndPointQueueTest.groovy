package org.gradle.messaging.remote.internal.hub.queue

class MultiEndPointQueueTest extends AbstractQueueTest {
    final MultiEndPointQueue queue = new MultiEndPointQueue(lock)

    def "forwards queued unicast messages to first waiting endpoint"() {
        given:
        def message1 = unicast()
        def message2 = unicast()
        def endpoint = queue.newEndpoint()

        and:
        queue.queue(message1)
        queue.queue(message2)

        when:
        queue.empty(endpoint)
        def messages = []
        endpoint.take(messages)

        then:
        messages == [message1, message2]
    }

    def "forwards unicast message to first waiting endpoint"() {
        given:
        def message = unicast()
        def endpoint = queue.newEndpoint()

        and:
        queue.empty(endpoint)

        when:
        queue.queue(message)
        def messages = []
        endpoint.take(messages)

        then:
        messages == [message]
    }

    def "forwards queued broadcast messages to all endpoints"() {
        given:
        def message1 = unicast()
        def message2 = broadcast()
        def message3 = unicast()
        def endpoint1 = queue.newEndpoint()
        def endpoint2 = queue.newEndpoint()

        and:
        queue.queue(message1)
        queue.queue(message2)
        queue.queue(message3)

        when:
        queue.empty(endpoint1)
        def messages1 = []
        endpoint1.take(messages1)
        def messages2 = []
        endpoint2.take(messages2)

        then:
        messages1 == [message1, message2, message3]
        messages2 == [message2]
    }

    def "forwards broadcast messages to all endpoints when nothing queued and nothing waiting"() {
        given:
        def message = broadcast()
        def message2 = unicast()
        def endpoint1 = queue.newEndpoint()
        def endpoint2 = queue.newEndpoint()

        when:
        queue.queue(message)
        queue.queue(message2)
        def messages1 = []
        endpoint1.take(messages1)
        def messages2 = []
        endpoint2.take(messages2)

        then:
        messages1 == [message]
        messages2 == [message]
    }

    def "forwards broadcast messages to all endpoints when nothing queued"() {
        given:
        def message = broadcast()
        def message2 = unicast()
        def endpoint1 = queue.newEndpoint()
        def endpoint2 = queue.newEndpoint()

        and:
        queue.empty(endpoint1)
        queue.empty(endpoint2)

        when:
        queue.queue(message)
        queue.queue(message2)
        def messages1 = []
        endpoint1.take(messages1)
        def messages2 = []
        endpoint2.take(messages2)

        then:
        messages1 == [message]
        messages2 == [message]
    }

    def "buffers messages when there are no endpoints"() {
        given:
        def message1 = broadcast()
        def message2 = unicast()
        def message3 = unicast()

        and:
        queue.queue(message1)
        queue.queue(message2)
        queue.queue(message3)

        when:
        def endpoint = queue.newEndpoint()
        def messages = []
        endpoint.take(messages)

        then:
        messages == [message1, message2, message3]
    }
}
