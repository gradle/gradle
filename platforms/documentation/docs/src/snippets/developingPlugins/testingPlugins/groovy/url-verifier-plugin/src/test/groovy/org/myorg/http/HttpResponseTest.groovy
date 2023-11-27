package org.myorg.http

import spock.lang.Specification

class HttpResponseTest extends Specification {

    private static final int OK_HTTP_CODE = 200
    private static final String OK_HTTP_MESSAGE = 'OK'

    def "can access information"() {
        when:
        def httpResponse = new HttpResponse(OK_HTTP_CODE, OK_HTTP_MESSAGE)

        then:
        httpResponse.code == OK_HTTP_CODE
        httpResponse.message == OK_HTTP_MESSAGE
    }

    def "can get String representation"() {
        when:
        def httpResponse = new HttpResponse(OK_HTTP_CODE, OK_HTTP_MESSAGE)

        then:
        httpResponse.toString() == "HTTP $OK_HTTP_CODE, Reason: $OK_HTTP_MESSAGE"
    }
}
