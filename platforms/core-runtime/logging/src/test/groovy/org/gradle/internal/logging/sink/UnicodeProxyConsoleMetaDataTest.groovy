/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.internal.logging.sink

import org.gradle.api.logging.configuration.ConsoleUnicodeSupport
import org.gradle.internal.nativeintegration.console.ConsoleMetaData
import spock.lang.Specification

class UnicodeProxyConsoleMetaDataTest extends Specification {
    ConsoleMetaData delegate = Mock(ConsoleMetaData)

    def "create with Auto returns the original metadata"() {
        when:
        def result = UnicodeProxyConsoleMetaData.create(delegate, ConsoleUnicodeSupport.Auto)

        then:
        result.is(delegate)
    }

    def "create with Disable returns proxy that disables unicode"() {
        given:
        delegate.supportsUnicode() >> true

        when:
        def result = UnicodeProxyConsoleMetaData.create(delegate, ConsoleUnicodeSupport.Disable)

        then:
        !result.supportsUnicode()
        !result.is(delegate)
    }

    def "create with Enable returns proxy that enables unicode"() {
        given:
        delegate.supportsUnicode() >> false

        when:
        def result = UnicodeProxyConsoleMetaData.create(delegate, ConsoleUnicodeSupport.Enable)

        then:
        result.supportsUnicode()
        !result.is(delegate)
    }

    def "proxy delegates isStdOutATerminal to wrapped metadata"() {
        given:
        delegate.isStdOutATerminal() >> expectedValue
        def proxy = UnicodeProxyConsoleMetaData.create(delegate, ConsoleUnicodeSupport.Disable)

        when:
        def result = proxy.isStdOutATerminal()

        then:
        result == expectedValue

        where:
        expectedValue << [true, false]
    }

    def "proxy delegates isStdErrATerminal to wrapped metadata"() {
        given:
        delegate.isStdErrATerminal() >> expectedValue
        def proxy = UnicodeProxyConsoleMetaData.create(delegate, ConsoleUnicodeSupport.Disable)

        when:
        def result = proxy.isStdErrATerminal()

        then:
        result == expectedValue

        where:
        expectedValue << [true, false]
    }

    def "proxy delegates getCols to wrapped metadata"() {
        given:
        delegate.getCols() >> expectedValue
        def proxy = UnicodeProxyConsoleMetaData.create(delegate, ConsoleUnicodeSupport.Disable)

        when:
        def result = proxy.getCols()

        then:
        result == expectedValue

        where:
        expectedValue << [0, 80, 120, 256]
    }

    def "proxy delegates getRows to wrapped metadata"() {
        given:
        delegate.getRows() >> expectedValue
        def proxy = UnicodeProxyConsoleMetaData.create(delegate, ConsoleUnicodeSupport.Disable)

        when:
        def result = proxy.getRows()

        then:
        result == expectedValue

        where:
        expectedValue << [0, 24, 40, 60]
    }

    def "proxy delegates isWrapStreams to wrapped metadata"() {
        given:
        delegate.isWrapStreams() >> expectedValue
        def proxy = UnicodeProxyConsoleMetaData.create(delegate, ConsoleUnicodeSupport.Disable)

        when:
        def result = proxy.isWrapStreams()

        then:
        result == expectedValue

        where:
        expectedValue << [true, false]
    }

    def "proxy delegates supportsTaskbarProgress to wrapped metadata"() {
        given:
        delegate.supportsTaskbarProgress() >> expectedValue
        def proxy = UnicodeProxyConsoleMetaData.create(delegate, ConsoleUnicodeSupport.Disable)

        when:
        def result = proxy.supportsTaskbarProgress()

        then:
        result == expectedValue

        where:
        expectedValue << [true, false]
    }

    def "disable proxy always returns false for supportsUnicode regardless of delegate value"() {
        given:
        delegate.supportsUnicode() >> delegateValue
        def proxy = UnicodeProxyConsoleMetaData.create(delegate, ConsoleUnicodeSupport.Disable)

        when:
        def result = proxy.supportsUnicode()

        then:
        !result

        where:
        delegateValue << [true, false]
    }

    def "enable proxy always returns true for supportsUnicode regardless of delegate value"() {
        given:
        delegate.supportsUnicode() >> delegateValue
        def proxy = UnicodeProxyConsoleMetaData.create(delegate, ConsoleUnicodeSupport.Enable)

        when:
        def result = proxy.supportsUnicode()

        then:
        result

        where:
        delegateValue << [true, false]
    }

    def "proxy delegates all methods correctly when unicode is enabled"() {
        given:
        delegate.isStdOutATerminal() >> true
        delegate.isStdErrATerminal() >> false
        delegate.getCols() >> 100
        delegate.getRows() >> 50
        delegate.isWrapStreams() >> true
        delegate.supportsTaskbarProgress() >> false
        delegate.supportsUnicode() >> false

        def proxy = UnicodeProxyConsoleMetaData.create(delegate, ConsoleUnicodeSupport.Enable)

        expect:
        proxy.isStdOutATerminal()
        !proxy.isStdErrATerminal()
        proxy.getCols() == 100
        proxy.getRows() == 50
        proxy.isWrapStreams()
        !proxy.supportsTaskbarProgress()
        proxy.supportsUnicode() // Should be true despite delegate returning false
    }

    def "proxy delegates all methods correctly when unicode is disabled"() {
        given:
        delegate.isStdOutATerminal() >> false
        delegate.isStdErrATerminal() >> true
        delegate.getCols() >> 80
        delegate.getRows() >> 24
        delegate.isWrapStreams() >> false
        delegate.supportsTaskbarProgress() >> true
        delegate.supportsUnicode() >> true

        def proxy = UnicodeProxyConsoleMetaData.create(delegate, ConsoleUnicodeSupport.Disable)

        expect:
        !proxy.isStdOutATerminal()
        proxy.isStdErrATerminal()
        proxy.getCols() == 80
        proxy.getRows() == 24
        !proxy.isWrapStreams()
        proxy.supportsTaskbarProgress()
        !proxy.supportsUnicode() // Should be false despite delegate returning true
    }
}

