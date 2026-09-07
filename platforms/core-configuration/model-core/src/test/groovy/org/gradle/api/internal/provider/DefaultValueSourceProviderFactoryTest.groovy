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
import org.gradle.api.logging.configuration.WarningMode
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.api.reflect.ObjectInstantiationException
import org.gradle.api.tasks.Nested
import org.gradle.internal.deprecation.DeprecationLogger
import org.gradle.internal.featurelifecycle.DefaultDeprecatedUsageProgressDetails
import org.gradle.internal.operations.BuildOperationProgressEventEmitter
import org.gradle.internal.state.Managed
import org.gradle.problems.buildtree.ProblemStream
import org.gradle.process.ExecOperations
import org.gradle.process.ExecResult
import org.gradle.util.TestUtil
import org.gradle.util.internal.RedirectStdOutAndErr
import org.gradle.util.internal.TextUtil
import org.junit.Rule
import spock.lang.Issue

import javax.inject.Inject

import static org.gradle.api.internal.provider.ValueSourceProviderFactory.ValueListener.ObtainedValue

class DefaultValueSourceProviderFactoryTest extends ValueSourceBasedSpec {

    @Rule
    RedirectStdOutAndErr outputs = new RedirectStdOutAndErr()
    def progressEventEmitter = Mock(BuildOperationProgressEventEmitter)

    def setup() {
        DeprecationLogger.reset()
        DeprecationLogger.init(WarningMode.All, progressEventEmitter, TestUtil.problemsService(), Stub(ProblemStream))
    }

    def cleanup() {
        DeprecationLogger.reset()
    }

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

    def "value source providers obtain value only once at #time time"() {

        given:
        configurationTimeBarrier.atConfigurationTime >> atConfigurationTime
        def provider = createProviderOf(EchoValueSource) {
            it.parameters.value.set('42')
        }

        when:
        provider.get()
        provider.get()
        provider.get()

        then:
        1 * valueListener.valueObtained(_, _)
        provider.get() == '42'

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

        when: "value is obtained for the 1st time"
        provider.get()

        then: "beforeValueObtained callback is notified"
        1 * computationListener.beforeValueObtained()

        then: "afterValueObtained callback is notified"
        1 * computationListener.afterValueObtained()

        then: "valueObtained is notified"
        1 * valueListener.valueObtained({ ObtainedValue it ->
            it.valueSourceType == EchoValueSource &&
                it.valueSourceParametersType == EchoValueSource.Parameters &&
                it.valueSourceParameters.value.get() == "42" &&
                it.value.get() == "42"
        }, { it instanceof EchoValueSource })

        when: "value is accessed a 2nd time"
        provider.get()

        then: "no notification is sent"
        0 * computationListener._
        0 * valueListener._
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

    def "parameterless value source parameters can be configured"() {
        when:
        def ran = false
        def provider = createProviderOf(NoParameters) {
            it.parameters {
                ran = true
            }
        }

        then:
        provider.get() == 42
        ran == true
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
        given:
        def provider = createProviderOf(StatusTrackingValueSource) {}

        when:
        def result = provider.get()

        then:
        1 * computationListener.beforeValueObtained() >> {
            StatusTrackingValueSource.INSIDE_COMPUTATION.set(true)
        }
        1 * computationListener.afterValueObtained() >> {
            StatusTrackingValueSource.INSIDE_COMPUTATION.set(false)
        }
        result
        !StatusTrackingValueSource.INSIDE_COMPUTATION.get()
    }

    def "failed value source notifies before-after listeners"() {
        given:
        def provider = createProviderOf(vsClass) {}

        when:
        provider.get()

        then:
        thrown(exceptionClass)
        1 * computationListener.beforeValueObtained()

        then:
        1 * computationListener.afterValueObtained()

        where:
        vsClass                        | exceptionClass
        ThrowingValueSource            | UnsupportedOperationException
        ConstructorThrowingValueSource | ObjectInstantiationException
    }

    def "failure to obtain value source notifies value listener"() {
        given:
        def provider = createProviderOf(ThrowingValueSource) {}

        when:
        try {
            provider.get()
        } catch (UnsupportedOperationException ignored) {
            // expected
        }

        then:
        1 * valueListener.valueObtained({ ObtainedValue it -> it.value.failure.isPresent() }, _)
    }

    def "failure to create value source does not notify value listener"() {
        given:
        def provider = createProviderOf(ConstructorThrowingValueSource) {}

        when:
        try {
            provider.get()
        } catch (ObjectInstantiationException ignored) {
            // expected
        }

        then:
        0 * valueListener.valueObtained(_, _)
    }

    @Issue("https://github.com/gradle/gradle/issues/39090")
    def "parameters injecting a service are deprecated"() {
        when:
        createProviderOf(InjectingParametersValueSource) {}

        then:
        1 * progressEventEmitter.emitNowIfCurrent({ DefaultDeprecatedUsageProgressDetails details ->
            details.summary == "Injecting services into value source parameters has been deprecated." &&
                details.removalDetails == "This will fail with an error in Gradle 10." &&
                details.contextualAdvice == "Type '${InjectingParametersValueSource.Parameters.name}' injects 'org.gradle.api.model.ObjectFactory'." &&
                details.advice == "Value source parameters must only hold data. Compute the value that needs the service when creating the provider and pass it as a parameter, or inject the service into the ValueSource implementation instead." &&
                details.documentationUrl.endsWith("/userguide/upgrading_version_9.html#value_source_parameters_service_injection")
        })
    }

    @Issue("https://github.com/gradle/gradle/issues/39090")
    def "deprecation is emitted once per parameters type"() {
        when:
        createProviderOf(InjectingParametersValueSource) {}
        createProviderOf(InjectingParametersValueSource) {}

        then:
        1 * progressEventEmitter.emitNowIfCurrent(_ as DefaultDeprecatedUsageProgressDetails)
    }

    @Issue("https://github.com/gradle/gradle/issues/39090")
    def "parameters that only hold data are not deprecated"() {
        when:
        createProviderOf(EchoValueSource) {
            it.parameters.value.set("42")
        }
        createProviderOf(NoParameters) {}
        createProviderOf(ExecValueSource) {}

        then:
        0 * progressEventEmitter._
    }

    @Issue("https://github.com/gradle/gradle/issues/39090")
    def "parameters nesting a type that injects a service are deprecated naming the nested type"() {
        when:
        createProviderOf(NestedInjectingParametersValueSource) {}

        then:
        1 * progressEventEmitter.emitNowIfCurrent({ DefaultDeprecatedUsageProgressDetails details ->
            details.contextualAdvice == "Type '${NestedInjectingParametersValueSource.Parameters.name}' injects 'org.gradle.api.model.ObjectFactory' through '${NestedInjectingParametersValueSource.Inner.name}'."
        })
    }

    @Issue("https://github.com/gradle/gradle/issues/39090")
    def "nested types that nest themselves are inspected once"() {
        when:
        createProviderOf(SelfNestingInjectingParametersValueSource) {}

        then:
        1 * progressEventEmitter.emitNowIfCurrent({ DefaultDeprecatedUsageProgressDetails details ->
            details.contextualAdvice == "Type '${SelfNestingInjectingParametersValueSource.Parameters.name}' injects 'org.gradle.api.model.ObjectFactory' through '${SelfNestingInjectingParametersValueSource.Inner.name}'."
        })
    }

    def "describable value source provides source information of missing value"() {
        given:
        def provider = createProviderOf(NullValueSourceWithDisplayName) {}

        when:
        provider.get()

        then:
        def e = thrown(MissingValueException)
        e.message == TextUtil.toPlatformLineSeparators("""Cannot query the value of this provider because it has no value available.
The value of this provider is derived from: nullValueSource""")
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

    static abstract class InjectingParametersValueSource implements ValueSource<String, Parameters> {

        interface Parameters extends ValueSourceParameters {
            @Inject
            ObjectFactory getObjects()
        }

        @Override
        String obtain() {
            return getParameters().getObjects().getClass().name
        }
    }

    static abstract class NestedInjectingParametersValueSource implements ValueSource<String, Parameters> {

        interface Inner {
            @Inject
            ObjectFactory getObjects()
        }

        interface Parameters extends ValueSourceParameters {
            @Nested
            Inner getInner()
        }

        @Override
        String obtain() {
            return getParameters().getInner().getObjects().getClass().name
        }
    }

    static abstract class SelfNestingInjectingParametersValueSource implements ValueSource<String, Parameters> {

        interface Inner {
            @Nested
            Inner getInner()

            @Inject
            ObjectFactory getObjects()
        }

        interface Parameters extends ValueSourceParameters {
            @Nested
            Inner getInner()
        }

        @Override
        String obtain() {
            return getParameters().getInner().getObjects().getClass().name
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

    static abstract class ConstructorThrowingValueSource implements ValueSource<Boolean, ValueSourceParameters.None> {
        ConstructorThrowingValueSource() {
            throw new UnsupportedOperationException("Cannot construct")
        }

        @Override
        Boolean obtain() {
            return false
        }
    }

    static abstract class NullValueSourceWithDisplayName implements ValueSource<Boolean, ValueSourceParameters.None>, Describable {
        @Override
        Boolean obtain() {
            return null
        }

        @Override
        String getDisplayName() {
            "nullValueSource"
        }
    }
}
