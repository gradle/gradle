package org.myorg.http

import spock.lang.Specification
import spock.lang.Subject

class DefaultHttpCallerIntegrationTest extends Specification {
    @Subject HttpCaller httpCaller = new DefaultHttpCaller()

    def "can make successful HTTP GET call"() {
        when:
        def httpResponse = httpCaller.get('https://www.google.com/')

        then:
        httpResponse.code == 200
        httpResponse.message == 'OK'
    }

    def "throws exception when calling unknown host via HTTP GET"() {
        when:
        httpCaller.get('https://www.wedonotknowyou123.com/')

        then:
        def t = thrown(HttpCallException)
        t.message == "Failed to call URL 'https://www.wedonotknowyou123.com/' via HTTP GET"
        t.cause instanceof UnknownHostException
    }
}
