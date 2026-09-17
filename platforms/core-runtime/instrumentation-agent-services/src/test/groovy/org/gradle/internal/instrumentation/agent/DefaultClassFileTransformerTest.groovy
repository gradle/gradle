/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.instrumentation.agent

import org.gradle.api.logging.LogLevel
import org.gradle.internal.classloader.InstrumentingClassLoader
import org.gradle.internal.logging.CollectingTestOutputEventListener
import org.gradle.internal.logging.ConfigureLogging
import org.junit.Rule
import spock.lang.Specification

import java.security.ProtectionDomain

class DefaultClassFileTransformerTest extends Specification {
    private static final byte[] ORIGINAL = [1, 2, 3] as byte[]
    private static final byte[] INSTRUMENTED = [4, 5, 6] as byte[]
    private static final String INTERNAL_NAME = TestClass.name.replace('.', '/')

    final CollectingTestOutputEventListener outputEventListener = new CollectingTestOutputEventListener()
    @Rule
    final ConfigureLogging logging = new ConfigureLogging(outputEventListener)

    final DefaultClassFileTransformer transformer = new DefaultClassFileTransformer()

    def "classes loaded by a classloader that doesn't instrument are left alone"() {
        expect:
        runTransform(getClass().classLoader) == null
        warnings().empty
    }

    def "substituting classloader warns that a redefinition had no effect"() {
        given:
        def loader = loader(canReinstrument: false)

        when:
        def result = runTransform(loader)

        then: "the pre-instrumented bytecode is served instead of the supplied one"
        Arrays.equals(result, INSTRUMENTED)

        and:
        warnings().size() == 1
        // The warning has to name the class the way a developer sees it, not in the JVM's internal form.
        warnings()[0].contains(TestClass.name)
        !warnings()[0].contains(INTERNAL_NAME)
    }

    def "no warning is emitted on an initial class load"() {
        given:
        def loader = loader(canReinstrument: false)

        when: "classBeingRedefined is null, so this is a plain class load rather than a redefinition"
        def result = runTransform(loader, null)

        then:
        Arrays.equals(result, INSTRUMENTED)
        warnings().empty
    }

    def "no warning is emitted when the classloader re-instruments the supplied bytecode"() {
        given: "the runtime-transformation path, where a redefinition does take effect"
        def loader = loader(canReinstrument: true)

        when:
        def result = runTransform(loader)

        then:
        Arrays.equals(result, INSTRUMENTED)
        warnings().empty
    }

    def "no warning is emitted when the redefined class is not instrumented by Gradle"() {
        given:
        def loader = loader(canReinstrument: false, instrumented: null)

        when:
        def result = runTransform(loader)

        then: "the class is left as the JVM supplied it, so the redefinition does take effect"
        result == null
        warnings().empty
    }

    def "instrumentation failure is reported to the classloader instead of being warned about"() {
        given:
        def failure = new RuntimeException("boom")
        def loader = loader(canReinstrument: false, failure: failure)

        when:
        def result = runTransform(loader)

        then:
        result == null
        loader.failures == [failure]
        warnings().empty
    }

    private byte[] runTransform(ClassLoader loader, Class<TestClass> classBeingRedefined = TestClass) {
        return transformer.transform(loader, INTERNAL_NAME, classBeingRedefined, TestClass.protectionDomain, ORIGINAL)
    }

    private List<String> warnings() {
        outputEventListener.events.findAll { it.logLevel == LogLevel.WARN }*.message
    }

    private static TestInstrumentingClassLoader loader(Map<String, ?> args) {
        new TestInstrumentingClassLoader(
            args.containsKey("instrumented") ? (byte[]) args.instrumented : INSTRUMENTED,
            (boolean) args.canReinstrument,
            (Throwable) args.failure
        )
    }

    private static class TestInstrumentingClassLoader extends ClassLoader implements InstrumentingClassLoader {
        private final byte[] instrumented
        private final boolean canReinstrument
        private final Throwable failure
        final List<Throwable> failures = []

        TestInstrumentingClassLoader(byte[] instrumented, boolean canReinstrument, Throwable failure) {
            this.instrumented = instrumented
            this.canReinstrument = canReinstrument
            this.failure = failure
        }

        @Override
        byte[] instrumentClass(String className, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
            if (failure != null) {
                throw failure
            }
            return instrumented
        }

        @Override
        boolean canReinstrumentClasses() {
            return canReinstrument
        }

        @Override
        void transformFailed(String className, Throwable cause) {
            failures << cause
        }
    }
}

