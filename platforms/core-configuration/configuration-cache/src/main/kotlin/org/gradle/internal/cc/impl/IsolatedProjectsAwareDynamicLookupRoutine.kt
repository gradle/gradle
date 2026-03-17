/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.cc.impl

import groovy.lang.MissingPropertyException
import org.gradle.api.internal.project.DynamicLookupRoutine
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.internal.configuration.problems.ProblemFactory
import org.gradle.internal.configuration.problems.ProblemsListener
import org.gradle.internal.metaobject.DynamicInvokeResult
import org.gradle.internal.metaobject.DynamicObject


/**
 * A project-scoped [DynamicLookupRoutine] that reports Isolated Projects violations
 * when the explicit Project property API is accessed from a build script.
 *
 * Violations are reported from the routine's own methods:
 * - [invokeMethod] reports when the method name is `"property"` or `"findProperty"`
 *   (these are script-level method calls like `property('name')`)
 * - [findProperty], [hasProperty], and [getProperties] always report
 *   (all call paths to these are explicit API calls)
 * - [property] does **not** report, because it is shared with [BasicScript.getProperty]
 *   for implicit property access (`version`, `tasks`, etc.)
 */
internal class IsolatedProjectsAwareDynamicLookupRoutine(
    private val delegate: DynamicLookupRoutine,
    private val project: ProjectInternal,
    private val problems: ProblemsListener,
    private val problemFactory: ProblemFactory
) : DynamicLookupRoutine {

    @Throws(MissingPropertyException::class)
    override fun property(receiver: DynamicObject, propertyName: String): Any? =
        delegate.property(receiver, propertyName)

    override fun findProperty(receiver: DynamicObject, propertyName: String): Any? =
        delegate.findProperty(receiver, propertyName)

    override fun setProperty(receiver: DynamicObject, name: String, value: Any?) =
        delegate.setProperty(receiver, name, value)

    override fun hasProperty(receiver: DynamicObject, propertyName: String): Boolean {
        reportViolation("hasProperty")
        return delegate.hasProperty(receiver, propertyName)
    }

    override fun getProperties(receiver: DynamicObject): Map<String, *>? {
        reportViolation("properties")
        return delegate.getProperties(receiver)
    }

    @Suppress("SpreadOperator")
    override fun invokeMethod(receiver: DynamicObject, name: String, args: Array<Any>): Any? {
        if (name == "property" || name == "findProperty") {
            reportViolation(name)
        }
        return delegate.invokeMethod(receiver, name, *args)
    }

    override fun tryGetProperty(receiver: DynamicObject, name: String): DynamicInvokeResult =
        delegate.tryGetProperty(receiver, name)

    @Suppress("ThrowingExceptionsWithoutMessageOrCause")
    private fun reportViolation(methodName: String) {
        val problem = problemFactory.problem {
            text("Project ")
            reference(project.identityPath.toString())
            text(" cannot use ")
            reference("Project.$methodName")
            text(" as these APIs are not supported with Isolated Projects")
        }
            .exception()
            .build()
        problems.onProblem(problem)
    }
}
