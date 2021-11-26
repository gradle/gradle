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

import static org.gradle.api.internal.provider.ValueSourceProviderFactory.Listener.ObtainedValue

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
        valueSourceProviderFactory.addListener {
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
        List<ObtainedValue<?, ValueSourceParameters>> obtainedValues = []
        valueSourceProviderFactory.addListener { obtainedValues.add(it) }

        when: "value is obtained for the 1st time"
        provider.get()

        then: "listener is notified"
        obtainedValues.size() == 1
        obtainedValues[0].valueSourceType == EchoValueSource
        obtainedValues[0].valueSourceParametersType == EchoValueSource.Parameters
        obtainedValues[0].valueSourceParameters.value.get() == "42"
        obtainedValues[0].value.get() == "42"

        when: "value is accessed a 2nd time"
        provider.get()

        then: "no notification is sent"
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
}
