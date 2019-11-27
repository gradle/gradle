package org.gradle.sample

import spock.lang.Specification

class LinkedListTest extends Specification {
    def "test LinkedList constructor"() {
        given:
        def list = new LinkedList()

        expect:
        list.size() == 0
    }

    def "test LinkedList#add"() {
        def list = new LinkedList()

        when:
        list.add('one');
        then:
        list.size() == 1
        list.get(0) == 'one'

        when:
        list.add('two');
        then:
        list.size() == 2
        list.get(1) == 'two'
    }

    def "test LinkedList#remove"() {
        def list = new LinkedList()

        given:
        list.add('one')
        list.add('two')

        expect:
        list.remove('one')
        list.size() == 1
        list.get(0) == 'two'

        list.remove("two")
        list.size() == 0
    }

    def "test LinkedList#remove for missing element"() {
        def list = new LinkedList()

        given:
        list.add("one")
        list.add("two")

        expect:
        !list.remove("three")
        list.size() == 2
    }
}
