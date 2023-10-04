/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.plugins.ide.eclipse.model

import org.gradle.api.JavaVersion
import org.gradle.api.internal.PropertiesTransformer

import spock.lang.Specification

class JdtTest extends Specification {
    final Jdt jdt = new Jdt(new PropertiesTransformer())

    def defaultsForJava1_3Source() {
        Properties properties = new Properties()

        when:
        jdt.loadDefaults()
        jdt.sourceCompatibility = JavaVersion.VERSION_1_3
        jdt.targetCompatibility = JavaVersion.VERSION_1_3
        store(properties)

        then:
        properties['org.eclipse.jdt.core.compiler.compliance'] == '1.3'
        properties['org.eclipse.jdt.core.compiler.source'] == '1.3'
        properties['org.eclipse.jdt.core.compiler.problem.assertIdentifier'] == 'ignore'
        properties['org.eclipse.jdt.core.compiler.problem.enumIdentifier'] == 'ignore'
        properties['org.eclipse.jdt.core.compiler.codegen.targetPlatform'] == '1.3'
    }

    def defaultsForJava1_4Source() {
        Properties properties = new Properties()

        when:
        jdt.loadDefaults()
        jdt.sourceCompatibility = JavaVersion.VERSION_1_4
        jdt.targetCompatibility = JavaVersion.VERSION_1_4
        store(properties)

        then:
        properties['org.eclipse.jdt.core.compiler.compliance'] == '1.4'
        properties['org.eclipse.jdt.core.compiler.source'] == '1.4'
        properties['org.eclipse.jdt.core.compiler.problem.assertIdentifier'] == 'error'
        properties['org.eclipse.jdt.core.compiler.problem.enumIdentifier'] == 'warning'
        properties['org.eclipse.jdt.core.compiler.codegen.targetPlatform'] == '1.4'
    }

    def defaultsForJava1_5Source() {
        Properties properties = new Properties()

        when:
        jdt.loadDefaults()
        jdt.sourceCompatibility = JavaVersion.VERSION_1_5
        jdt.targetCompatibility = JavaVersion.VERSION_1_5
        store(properties)

        then:
        properties['org.eclipse.jdt.core.compiler.compliance'] == '1.5'
        properties['org.eclipse.jdt.core.compiler.source'] == '1.5'
        properties['org.eclipse.jdt.core.compiler.problem.assertIdentifier'] == 'error'
        properties['org.eclipse.jdt.core.compiler.problem.enumIdentifier'] == 'error'
        properties['org.eclipse.jdt.core.compiler.codegen.targetPlatform'] == '1.5'
    }

    def defaultsForJava1_6Source() {
        Properties properties = new Properties()

        when:
        jdt.loadDefaults()
        jdt.sourceCompatibility = JavaVersion.VERSION_1_6
        jdt.targetCompatibility = JavaVersion.VERSION_1_6
        store(properties)

        then:
        properties['org.eclipse.jdt.core.compiler.compliance'] == '1.6'
        properties['org.eclipse.jdt.core.compiler.source'] == '1.6'
        properties['org.eclipse.jdt.core.compiler.problem.assertIdentifier'] == 'error'
        properties['org.eclipse.jdt.core.compiler.problem.enumIdentifier'] == 'error'
        properties['org.eclipse.jdt.core.compiler.codegen.targetPlatform'] == '1.6'
    }

    def store(Properties properties) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream()
        jdt.store(outputStream)
        properties.load(new ByteArrayInputStream(outputStream.toByteArray()))
    }
}
