/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.ide.xcode.internal

import org.gradle.api.Project
import spock.lang.Specification
import spock.lang.Subject

class XcodePropertyAdapterTest extends Specification {
    def project = Mock(Project)

    @Subject
    def xcodePropertyAdapter = new XcodePropertyAdapter(project)

    def "getAction"() {
        expectProperty("_XCODE_ACTION", "myaction")

        expect:
        xcodePropertyAdapter.action == "" // default value
        xcodePropertyAdapter.action == "myaction"
    }

    def "getProductName"() {
        expectProperty("_XCODE_PRODUCT_NAME", "myproduct")

        expect:
        xcodePropertyAdapter.productName == "" // default value
        xcodePropertyAdapter.productName == "myproduct"
    }

    def "getConfiguration"() {
        expectProperty("_XCODE_CONFIGURATION", "configuration")

        expect:
        xcodePropertyAdapter.configuration == "" // default value
        xcodePropertyAdapter.configuration == "configuration"
    }

    def "getBuiltProductsDir"() {
        expectProperty("_XCODE_BUILT_PRODUCTS_DIR", "dir")

        expect:
        xcodePropertyAdapter.builtProductsDir == "" // default value
        xcodePropertyAdapter.builtProductsDir == "dir"
    }

    def "getAdapterCommandLine"() {
        expect:
        XcodePropertyAdapter.adapterCommandLine == [
                '-P_XCODE_ACTION="${ACTION}"',
                '-P_XCODE_PRODUCT_NAME="${PRODUCT_NAME}"',
                '-P_XCODE_CONFIGURATION="${CONFIGURATION}"',
                '-P_XCODE_BUILT_PRODUCTS_DIR="${BUILT_PRODUCTS_DIR}"',
        ]
    }

    private void expectProperty(String propertyName, String expected) {
        1 * project.findProperty(propertyName)
        1 * project.findProperty(propertyName) >> expected
    }
}
