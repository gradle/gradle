/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.kotlin.dsl.support

import org.gradle.api.internal.GradleApiImplicitImportsProvider
import org.gradle.configuration.ImportsReader
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope


/**
 * Holds the list of imports implicitly added to every Kotlin build script.
 */
@ServiceScope(Scope.Global::class)
class ImplicitImports internal constructor(
    @Transient
    private val importsReader: ImportsReader
) : GradleApiImplicitImportsProvider {

    companion object Constants {
        val groovySpecificImplicitImports by lazy {
            listOf(
                "java.lang.*",
                "java.io.*",
                "java.net.*",
                "java.util.*",
                "java.time.*",
                "java.math.BigDecimal",
                "java.math.BigInteger",
                "javax.inject.Inject"
            )
        }

        val kotlinSpecificImplicitImports by lazy {
            listOf(
                "org.gradle.kotlin.dsl.*",
                // TODO: let this be contributed by :plugins
                "org.gradle.kotlin.dsl.plugins.dsl.*",
                // TODO: infer list of types below at build time by inspecting the Gradle API
                "java.util.concurrent.Callable",
                "java.util.concurrent.TimeUnit",
                "java.math.BigDecimal",
                "java.math.BigInteger",
                "java.io.File",
                "javax.inject.Inject"
            )
        }

        val kotlinImplicitImportApproximations by lazy {
            listOf(
                "org.gradle.*",
                "org.gradle.api.*",
                "org.gradle.api.artifacts.*",
                "org.gradle.api.artifacts.capability.*",
                "org.gradle.api.artifacts.component.*",
                "org.gradle.api.artifacts.dsl.*",
                "org.gradle.api.artifacts.ivy.*",
                "org.gradle.api.artifacts.maven.*",
                "org.gradle.api.artifacts.query.*",
                "org.gradle.api.artifacts.repositories.*",
                "org.gradle.api.artifacts.result.*",
                "org.gradle.api.artifacts.transform.*",
                "org.gradle.api.artifacts.type.*",
                "org.gradle.api.artifacts.verification.*",
                "org.gradle.api.attributes.*",
                "org.gradle.api.attributes.java.*",
                "org.gradle.api.attributes.plugin.*",
                "org.gradle.api.cache.*",
                "org.gradle.api.capabilities.*",
                "org.gradle.api.component.*",
                "org.gradle.api.configuration.*",
                "org.gradle.api.credentials.*",
                "org.gradle.api.distribution.*",
                "org.gradle.api.distribution.plugins.*",
                "org.gradle.api.execution.*",
                "org.gradle.api.file.*",
                "org.gradle.api.flow.*",
                "org.gradle.api.initialization.*",
                "org.gradle.api.initialization.definition.*",
                "org.gradle.api.initialization.dsl.*",
                "org.gradle.api.initialization.resolve.*",
                "org.gradle.api.invocation.*",
                "org.gradle.api.java.archives.*",
                "org.gradle.api.jvm.*",
                "org.gradle.api.launcher.cli.*",
                "org.gradle.api.logging.*",
                "org.gradle.api.logging.configuration.*",
                "org.gradle.api.model.*",
                "org.gradle.api.plugins.*",
                "org.gradle.api.plugins.catalog.*",
                "org.gradle.api.plugins.jvm.*",
                "org.gradle.api.plugins.quality.*",
                "org.gradle.api.plugins.scala.*",
                "org.gradle.api.problems.*",
                "org.gradle.api.project.*",
                "org.gradle.api.provider.*",
                "org.gradle.api.publish.*",
                "org.gradle.api.publish.ivy.*",
                "org.gradle.api.publish.ivy.plugins.*",
                "org.gradle.api.publish.ivy.tasks.*",
                "org.gradle.api.publish.maven.*",
                "org.gradle.api.publish.maven.plugins.*",
                "org.gradle.api.publish.maven.tasks.*",
                "org.gradle.api.publish.plugins.*",
                "org.gradle.api.publish.tasks.*",
                "org.gradle.api.reflect.*",
                "org.gradle.api.reporting.*",
                "org.gradle.api.reporting.components.*",
                "org.gradle.api.reporting.dependencies.*",
                "org.gradle.api.reporting.dependents.*",
                "org.gradle.api.reporting.model.*",
                "org.gradle.api.reporting.plugins.*",
                "org.gradle.api.resources.*",
                "org.gradle.api.services.*",
                "org.gradle.api.specs.*",
                "org.gradle.api.tasks.*",
                "org.gradle.api.tasks.ant.*",
                "org.gradle.api.tasks.application.*",
                "org.gradle.api.tasks.bundling.*",
                "org.gradle.api.tasks.compile.*",
                "org.gradle.api.tasks.diagnostics.*",
                "org.gradle.api.tasks.diagnostics.artifact.transforms.*",
                "org.gradle.api.tasks.diagnostics.configurations.*",
                "org.gradle.api.tasks.incremental.*",
                "org.gradle.api.tasks.javadoc.*",
                "org.gradle.api.tasks.options.*",
                "org.gradle.api.tasks.scala.*",
                "org.gradle.api.tasks.testing.*",
                "org.gradle.api.tasks.testing.junit.*",
                "org.gradle.api.tasks.testing.junitplatform.*",
                "org.gradle.api.tasks.testing.source.*",
                "org.gradle.api.tasks.testing.testng.*",
                "org.gradle.api.tasks.util.*",
                "org.gradle.api.toolchain.management.*",
                "org.gradle.authentication.*",
                "org.gradle.authentication.aws.*",
                "org.gradle.authentication.http.*",
                "org.gradle.build.event.*",
                "org.gradle.buildconfiguration.tasks.*",
                "org.gradle.buildinit.specs.*",
                "org.gradle.caching.*",
                "org.gradle.caching.configuration.*",
                "org.gradle.caching.http.*",
                "org.gradle.caching.local.*",
                "org.gradle.concurrent.*",
                "org.gradle.external.javadoc.*",
                "org.gradle.ivy.*",
                "org.gradle.jvm.*",
                "org.gradle.jvm.application.scripts.*",
                "org.gradle.jvm.toolchain.*",
                "org.gradle.language.base.*",
                "org.gradle.language.base.artifact.*",
                "org.gradle.language.base.compile.*",
                "org.gradle.language.base.plugins.*",
                "org.gradle.language.base.sources.*",
                "org.gradle.language.java.artifact.*",
                "org.gradle.language.jvm.tasks.*",
                "org.gradle.language.scala.tasks.*",
                "org.gradle.maven.*",
                "org.gradle.model.*",
                "org.gradle.nativeplatform.*",
                "org.gradle.normalization.*",
                "org.gradle.platform.*",
                "org.gradle.platform.base.*",
                "org.gradle.platform.base.binary.*",
                "org.gradle.platform.base.component.*",
                "org.gradle.platform.base.plugins.*",
                "org.gradle.plugin.devel.*",
                "org.gradle.plugin.devel.plugins.*",
                "org.gradle.plugin.devel.tasks.*",
                "org.gradle.plugin.management.*",
                "org.gradle.plugin.use.*",
                "org.gradle.plugins.ear.*",
                "org.gradle.plugins.ear.descriptor.*",
                "org.gradle.plugins.ide.*",
                "org.gradle.plugins.ide.api.*",
                "org.gradle.plugins.ide.eclipse.*",
                "org.gradle.plugins.ide.idea.*",
                "org.gradle.plugins.signing.*",
                "org.gradle.plugins.signing.signatory.*",
                "org.gradle.plugins.signing.signatory.pgp.*",
                "org.gradle.plugins.signing.type.*",
                "org.gradle.plugins.signing.type.pgp.*",
                "org.gradle.process.*",
                "org.gradle.testing.base.*",
                "org.gradle.testing.base.plugins.*",
                "org.gradle.testing.jacoco.plugins.*",
                "org.gradle.testing.jacoco.tasks.*",
                "org.gradle.testing.jacoco.tasks.rules.*",
                "org.gradle.testkit.runner.*",
                "org.gradle.util.*",
                "org.gradle.vcs.*",
                "org.gradle.vcs.git.*",
                "org.gradle.work.*",
                "org.gradle.workers.*",
                "org.gradle.kotlin.dsl.*",
                "org.gradle.kotlin.dsl.plugins.dsl.*",
                "java.util.concurrent.Callable",
                "java.util.concurrent.TimeUnit",
                "java.math.BigDecimal",
                "java.math.BigInteger",
                "java.io.File",
                "javax.inject.Inject"
            )
        } // TODO: how to get an accurate hardcoded version, when needing to use it in annotations?
    }

    private
    val defaultGradleApiImports by lazy {
        importsReader.simpleNameToFullClassNamesMapping.values.map { it.first() }
    }

    override fun getGroovyDslImplicitImports(): List<String> =
        defaultGradleApiImports + groovySpecificImplicitImports

    override fun getKotlinDslImplicitImports(): List<String> =
        defaultGradleApiImports + kotlinSpecificImplicitImports

    val list = kotlinDslImplicitImports

}
