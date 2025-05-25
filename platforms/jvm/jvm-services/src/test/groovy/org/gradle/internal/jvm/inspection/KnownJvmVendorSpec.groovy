/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.jvm.inspection

import spock.lang.Specification

class KnownJvmVendorSpec extends Specification {

    def 'Known vendors match their indicator string'() {
        expect:
        vendor == JvmVendor.KnownJvmVendor.parse(vendor.indicatorString)

        where:
        vendor << JvmVendor.KnownJvmVendor.values()
    }

    def 'Adoptium matches multiple vendor strings'() {
        expect:
        JvmVendor.KnownJvmVendor.ADOPTIUM == JvmVendor.KnownJvmVendor.parse(vendorString)

        where:
        vendorString << ['Adoptium', 'Temurin', 'Eclipse Foundation', 'Eclipse Temurin']
    }

    def 'AdoptOpenJDK matches multiple vendor strings'() {
        expect:
        JvmVendor.KnownJvmVendor.ADOPTOPENJDK == JvmVendor.KnownJvmVendor.parse(vendorString)

        where:
        vendorString << ['AdoptOpenJDK', 'AOJ']
    }

    def 'Amazon matches multiple vendor strings'() {
        expect:
        JvmVendor.KnownJvmVendor.AMAZON == JvmVendor.KnownJvmVendor.parse(vendorString)

        where:
        vendorString << ['Amazon', 'Corretto', 'Amazon Corretto']
    }

    def 'Azul matches multiple vendor strings'() {
        expect:
        JvmVendor.KnownJvmVendor.AZUL == JvmVendor.KnownJvmVendor.parse(vendorString)

        where:
        vendorString << ['Azul', 'Zulu', 'Azul Systems', 'Azul Zulu']
    }

    def 'BellSoft matches multiple vendor strings'() {
        expect:
        JvmVendor.KnownJvmVendor.BELLSOFT == JvmVendor.KnownJvmVendor.parse(vendorString)

        where:
        vendorString << ['BellSoft', 'Liberica', 'BellSoft Liberica']
    }

    def 'GraalVM matches multiple vendor strings'() {
        expect:
        JvmVendor.KnownJvmVendor.GRAAL_VM == JvmVendor.KnownJvmVendor.parse(vendorString)

        where:
        vendorString << ['GraalVM', 'GraalVM Community', 'Graal VM']
    }

    def 'Hewlett matches multiple vendor strings'() {
        expect:
        JvmVendor.KnownJvmVendor.HEWLETT_PACKARD == JvmVendor.KnownJvmVendor.parse(vendorString)

        where:
        vendorString << ['HP', 'Hewlett', 'Hewlett Packard']
    }

    def 'IBM matches multiple vendor strings'() {
        expect:
        JvmVendor.KnownJvmVendor.IBM == JvmVendor.KnownJvmVendor.parse(vendorString)

        where:
        vendorString << ['IBM', 'Semeru', 'IBM Semeru', 'International Business Machines Corporation']
    }

    def 'Jetbrains matches multiple vendor strings'() {
        expect:
        JvmVendor.KnownJvmVendor.JETBRAINS == JvmVendor.KnownJvmVendor.parse(vendorString)

        where:
        vendorString << ['JBR', 'JetBrains', 'JetBrains Runtime']
    }

    def 'Oracle matches multiple vendor strings'() {
        expect:
        JvmVendor.KnownJvmVendor.ORACLE == JvmVendor.KnownJvmVendor.parse(vendorString)

        where:
        vendorString << ['Oracle', 'Oracle OpenJDK']
    }

    def 'SAP matches multiple vendor strings'() {
        expect:
        JvmVendor.KnownJvmVendor.SAP == JvmVendor.KnownJvmVendor.parse(vendorString)

        where:
        vendorString << ['SAP', 'SAP SE', 'SAP Machine']
    }

    def 'Tencent matches multiple vendor strings'() {
        expect:
        JvmVendor.KnownJvmVendor.TENCENT == JvmVendor.KnownJvmVendor.parse(vendorString)

        where:
        vendorString << ['Tencent', 'Kona', 'Tencent Kona']
    }
}
