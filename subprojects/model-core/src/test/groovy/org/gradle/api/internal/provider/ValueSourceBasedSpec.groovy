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

import org.gradle.api.Action
import org.gradle.api.internal.properties.GradleProperties
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.api.provider.ValueSourceSpec
import org.gradle.internal.event.DefaultListenerManager
import org.gradle.internal.service.scopes.Scopes
import org.gradle.internal.snapshot.impl.DefaultIsolatableFactory
import org.gradle.process.ExecOperations
import org.gradle.util.TestUtil
import spock.lang.Specification

abstract class ValueSourceBasedSpec extends Specification {

    def listenerManager = new DefaultListenerManager(Scopes.Build)
    def isolatableFactory = new DefaultIsolatableFactory(
        null,
        TestUtil.managedFactoryRegistry()
    )
    def configurationTimeBarrier = Mock(ConfigurationTimeBarrier)
    def execOperations = Mock(ExecOperations)
    def valueSourceProviderFactory = new DefaultValueSourceProviderFactory(
        listenerManager,
        TestUtil.instantiatorFactory(),
        isolatableFactory,
        Mock(GradleProperties),
        execOperations,
        TestUtil.services()
    )

    protected <T, P extends ValueSourceParameters> Provider<T> createProviderOf(Class<? extends ValueSource<T, P>> valueSourceType, Action<? super ValueSourceSpec<P>> configureAction) {
        return valueSourceProviderFactory.createProviderOf(valueSourceType, configureAction)
    }
}
