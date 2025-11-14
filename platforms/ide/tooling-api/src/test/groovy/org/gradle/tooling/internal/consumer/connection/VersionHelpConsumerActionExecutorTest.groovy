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
package org.gradle.tooling.internal.consumer.connection

import org.gradle.tooling.internal.consumer.LoggingProvider
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters
import org.gradle.tooling.model.build.Help
import org.gradle.tooling.model.build.VersionBanner
import spock.lang.Specification

class VersionHelpConsumerActionExecutorTest extends Specification {
    def delegate = new TestDelegate()
    def logging = Mock(LoggingProvider)
    def exec = new VersionHelpConsumerActionExecutor(delegate, logging)

    def "prints help and short-circuits"() {
        given:
        delegate.reset()
        def out = new ByteArrayOutputStream()
        def params = ConsumerOperationParameters.builder()
            .setEntryPoint("test")
            .setParameters(null)
            .setStdout(out)
            .setArguments(["--help"]) // includes -h/-? too
            .build()

    and:
    def help = Stub(Help) { getHelpOutput() >> "HELP" }
    def connection = Stub(ConsumerConnection) {
        run(_, _) >> help
    }
    delegate.conn = connection

        when:
        def result = exec.run(new DummyAction(params))

    then:
    result == null
    new String(out.toByteArray(), 'UTF-8').contains('HELP')
    delegate.calls == 1
    }

    def "prints version and short-circuits"() {
        given:
        delegate.reset()
        def out = new ByteArrayOutputStream()
        def params = ConsumerOperationParameters.builder()
            .setEntryPoint("test")
            .setParameters(null)
            .setStdout(out)
            .setArguments(["--version"]) // includes -v too
            .build()
    and:
    def banner = Stub(VersionBanner) { getVersionOutput() >> "VER" }
    def connection2 = Stub(ConsumerConnection) {
        run(_, _) >> banner
    }
    delegate.conn = connection2

        when:
        def result = exec.run(new DummyAction(params))

    then:
    result == null
    new String(out.toByteArray(), 'UTF-8').contains('VER')
    delegate.calls == 1
    }

    def "prints show-version and continues without -V"() {
        given:
        delegate.reset()
        def out = new ByteArrayOutputStream()
        def params = ConsumerOperationParameters.builder()
            .setEntryPoint("test")
            .setParameters(null)
            .setStdout(out)
            .setArguments(["-V", "--info"]) // -V to be stripped
            .build()
        and:
        def banner = Stub(VersionBanner) { getVersionOutput() >> "VER2" }
        def call = 0
        def connection3 = Stub(ConsumerConnection) {
            run(_, _) >> banner
        }
        delegate.conn = connection3

        when:
        def result = exec.run(new DummyAction(params))

        then:
    result == null
    new String(out.toByteArray(), 'UTF-8').contains('VER2')
        delegate.calls == 2
        // Verify the second delegated action receives filtered args
        assert delegate.actions.size() == 2
        assert delegate.actions.get(1).parameters.getArguments() == ["--info"]
    }

    static class DummyAction<T> implements ConsumerAction<T> {
        final ConsumerOperationParameters p
        DummyAction(ConsumerOperationParameters p) { this.p = p }
        @Override ConsumerOperationParameters getParameters() { return p }
        @Override T run(ConsumerConnection connection) { return null }
    }

    static class TestDelegate implements ConsumerActionExecutor {
        ConsumerConnection conn
        int calls = 0
        List<ConsumerAction> actions = []

        @Override
        void stop() { }

        @Override
        String getDisplayName() { return "test" }

        @Override
        def <T> T run(ConsumerAction<T> action) throws UnsupportedOperationException, IllegalStateException {
            calls++
            actions.add(action)
            return action.run(conn)
        }

        @Override
        void disconnect() { }

        void reset() {
            calls = 0
            actions.clear()
        }
    }
}
