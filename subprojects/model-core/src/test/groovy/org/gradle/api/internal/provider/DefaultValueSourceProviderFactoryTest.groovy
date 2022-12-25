/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.internal.provider


import org.gradle.api.Describable
import org.gradle.api.GradleException
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.internal.state.Managed
import org.gradle.process.ExecOperations
import org.gradle.process.ExecResult

import javax.inject.Inject

import static org.gradle.api.internal.provider.ValueSourceProviderFactory.ValueListener.ObtainedValue

class DefaultValueSourceProviderFactoryTest extends ValueSourceBasedSpec {

    def "parameters are configured eagerly"() {

        given:
        def configured = false

        when:
        createProviderOf(EchoValueSource) {
            configured = true
        }

        then:
        configured
    }

    def "provider forUseAtConfigurationTime is a no-op"() {

        given:
        configurationTimeBarrier.atConfigurationTime >> true
        def provider = createProviderOf(EchoValueSource) {
            it.parameters.value.set('42')
        }
        def configTimeProvider = provider.forUseAtConfigurationTime()

        expect:
        configTimeProvider === provider
    }

    def "providers forUseAtConfigurationTime obtain value only once at #time time"() {

        given:
        configurationTimeBarrier.atConfigurationTime >> atConfigurationTime
        def provider = createProviderOf(EchoValueSource) {
            it.parameters.value.set('42')
        }
        def obtainedValueCount = 0
        valueSourceProviderFactory.addValueListener { value, source ->
            assert source instanceof EchoValueSource
            obtainedValueCount += 1
        }

        expect:
        provider.get() == '42'
        provider.get() == '42'
        provider.get() == '42'
        obtainedValueCount == 1

        where:
        time            | atConfigurationTime
        'configuration' | true
        'execution'     | false
    }

    def "listener is notified when value is obtained"() {

        given: "a listener is connected to the provider factory"
        def provider = createProviderOf(EchoValueSource) {
            it.parameters.value.set("42")
        }
        ValueSourceProviderFactory.ComputationListener computationListener = Mock()
        valueSourceProviderFactory.addComputationListener(computationListener)
        List<ObtainedValue<?, ValueSourceParameters>> obtainedValues = []
        valueSourceProviderFactory.addValueListener { value, source ->
            assert source instanceof EchoValueSource
            obtainedValues.add(value)
        }

        when: "value is obtained for the 1st time"
        provider.get()

        then: "beforeValueObtained callback is notified"
        1 * computationListener.beforeValueObtained()

        then: "afterValueObtained callback is notified"
        1 * computationListener.afterValueObtained()

        then: "valueObtained is notified"
        obtainedValues.size() == 1
        obtainedValues[0].valueSourceType == EchoValueSource
        obtainedValues[0].valueSourceParametersType == EchoValueSource.Parameters
        obtainedValues[0].valueSourceParameters.value.get() == "42"
        obtainedValues[0].value.get() == "42"

        when: "value is accessed a 2nd time"
        provider.get()

        then: "no notification is sent"
        0 * computationListener._
        obtainedValues.size() == 1
    }

    def "provider maps null returned from obtain to not present"() {

        given:
        def provider = createProviderOf(EchoValueSource) {
            // give no value so `getParameters().getValue().getOrNull()` returns null
        }

        expect:
        !provider.isPresent()
        provider.getOrNull() == null
    }

    def "provider assumes graph is immutable"() {

        given:
        def provider = createProviderOf(EchoValueSource) {}

        expect:
        ((Managed) provider).isImmutable()
    }

    def "parameterless value source can be used"() {

        given:
        def provider = createProviderOf(NoParameters) {}

        expect:
        provider.get() == 42
    }

    def "parameterless value source parameters cannot be configured"() {

        when:
        createProviderOf(NoParameters) {
            it.parameters {}
        }

        then:
        def e = thrown(GradleException)
        e.message == 'Could not create provider for value source DefaultValueSourceProviderFactoryTest.NoParameters.'
        e.cause.message == 'Value is null'
    }

    def "value source can get ExecOperations injected"() {
        when:
        def provider = createProviderOf(ExecValueSource) {
            it.parameters {
                it.command = ["echo", "hello"]
            }
        }
        provider.get()

        then:
        1 * execOperations.exec(_) >> _
    }

    def "listener calls wrap obtain invocation"() {
        when:
        valueSourceProviderFactory.addComputationListener(new ValueSourceProviderFactory.ComputationListener() {
            @Override
            void beforeValueObtained() {
                StatusTrackingValueSource.INSIDE_COMPUTATION.set(true)
            }

            @Override
            void afterValueObtained() {
                StatusTrackingValueSource.INSIDE_COMPUTATION.set(false)
            }
        })
        def provider = createProviderOf(StatusTrackingValueSource) {}

        def result = provider.get()

        then:
        result
        !StatusTrackingValueSource.INSIDE_COMPUTATION.get()
    }

    def "failed value source restores state"() {
        given:
        ValueSourceProviderFactory.ComputationListener listener = Mock()
        valueSourceProviderFactory.addComputationListener(listener)

        when:
        def provider = createProviderOf(ThrowingValueSource) {}
        try {
            provider.get()
        } catch (UnsupportedOperationException ignored) {
            // expected
        }

        then:
        1 * listener.beforeValueObtained()
        then:
        1 * listener.afterValueObtained()
    }

    static abstract class EchoValueSource implements ValueSource<String, Parameters> {

        interface Parameters extends ValueSourceParameters {
            Property<String> getValue()
        }

        @Override
        String obtain() {
            return getParameters().getValue().getOrNull()
        }
    }

    static abstract class EchoValueSourceWithDisplayName extends EchoValueSource
        implements Describable {

        @Override
        String getDisplayName() {
            "echo(${getParameters().value.orElse('?').get()})"
        }
    }

    static abstract class NoParameters implements ValueSource<Integer, ValueSourceParameters.None> {

        @Override
        Integer obtain() {
            return 42
        }
    }

    static abstract class ExecValueSource implements ValueSource<ExecResult, Parameters> {
        final ExecOperations execOperations
        interface Parameters extends ValueSourceParameters {
            ListProperty<String> getCommand()
        }

        @Inject
        ExecValueSource(ExecOperations execOperations) {
            this.execOperations = execOperations
        }

        @Override
        ExecResult obtain() {
            return execOperations.exec {
                commandLine(getParameters().command.get())
            }
        }
    }

    static abstract class StatusTrackingValueSource implements ValueSource<Boolean, ValueSourceParameters.None> {
        static final ThreadLocal<Boolean> INSIDE_COMPUTATION = ThreadLocal.withInitial(() -> false)

        private boolean isInsideComputationInConstructor
        StatusTrackingValueSource() {
            isInsideComputationInConstructor = INSIDE_COMPUTATION.get()
        }

        @Override
        Boolean obtain() {
            return isInsideComputationInConstructor && INSIDE_COMPUTATION.get()
        }
    }

    static abstract class ThrowingValueSource implements ValueSource<Boolean, ValueSourceParameters.None> {
        @Override
        Boolean obtain() {
            throw new UnsupportedOperationException("Cannot compute value")
        }
    }
}
