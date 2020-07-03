/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.jvm.toolchain.internal

import org.gradle.api.internal.provider.DefaultProperty
import org.gradle.api.internal.provider.PropertyHost
import org.gradle.api.provider.ProviderFactory
import spock.lang.Specification

class CurrentInstallationSupplierTest extends Specification {

    def "supplies java home as installation"() {
        given:
        def supplier = createSupplier("/foo/bar")

        when:
        def directories = supplier.get()

        then:
        directories*.location == [new File("/foo/bar")]
        directories*.source == ["current jvm"]
    }

    private createSupplier(String propertyValue) {
        new CurrentInstallationSupplier(createProviderFactory(propertyValue))
    }

    private ProviderFactory createProviderFactory(String propertyValue) {
        def providerFactory = Mock(ProviderFactory)
        def provider = new DefaultProperty(PropertyHost.NO_OP, String)
        provider.set(propertyValue)
        providerFactory.systemProperty("java.home") >> provider
        providerFactory
    }

}
