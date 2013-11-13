package org.gradle.initialization.progress

import spock.lang.Specification

class SimpleProgressFormatterTest extends Specification {

    def "formats progress"() {
        def f = new SimpleProgressFormatter(10, "things")

        expect:
        f.progress == "0/10 things"
        f.incrementAndGetProgress() == "1/10 things"
        f.incrementAndGetProgress() == "2/10 things"
        f.progress == "2/10 things"
    }

    def "does not allow overflow"() {
        def f = new SimpleProgressFormatter(2, "cats");
        f.incrementAndGetProgress()
        f.incrementAndGetProgress()

        when:
        f.incrementAndGetProgress()

        then:
        thrown(IllegalStateException)
    }
}
