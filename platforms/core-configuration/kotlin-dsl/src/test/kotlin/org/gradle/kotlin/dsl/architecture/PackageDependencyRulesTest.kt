/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.kotlin.dsl.architecture

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses


import org.junit.Test


class PackageDependencyRulesTest {

    @Test
    fun `execution should not depend on provider`() {
        "..execution.." shouldNotDependOn "..provider.."
    }

    @Test
    fun `execution should not depend on resolver`() {
        "..execution.." shouldNotDependOn "..resolver.."
    }

    @Test
    fun `support should not depend on provider`() {
        "..support.." shouldNotDependOn "..provider.."
    }

    @Test
    fun `support should not depend on execution`() {
        "..support.." shouldNotDependOn "..execution.."
    }

    @Test
    fun `support should not depend on codegen`() {
        "..support.." shouldNotDependOn "..kotlin.dsl.codegen.."
    }

    @Test
    fun `accessors should not depend on execution`() {
        "..accessors.." shouldNotDependOn "..kotlin.dsl.execution.."
    }

    private
    infix fun String.shouldNotDependOn(thatPackage: String) {
        noClasses()
            .that().resideInAPackage(this)
            .should().accessClassesThat().resideInAPackage(thatPackage)
            .check(classes)
    }

    private
    val classes =
        ClassFileImporter()
            .importPackagesOf(
                org.gradle.kotlin.dsl.execution.Interpreter::class.java,
                org.gradle.kotlin.dsl.provider.KotlinScriptClassPathProvider::class.java,
                org.gradle.kotlin.dsl.support.EmbeddedKotlinProvider::class.java,
                org.gradle.kotlin.dsl.accessors.ProjectSchema::class.java
            )
}
