/*
 * Copyright 2022 the original author or authors.
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

import groovy.lang.MissingMethodException
import groovy.lang.MissingPropertyException
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.internal.configuration.problems.ProblemFactory
import org.gradle.internal.configuration.problems.ProblemsListener
import org.gradle.internal.configuration.problems.PropertyKind
import org.gradle.internal.configuration.problems.PropertyTrace
import org.gradle.internal.metaobject.DynamicInvokeResult
import org.gradle.internal.metaobject.DynamicObject
import java.util.Locale


internal
class CrossProjectModelAccessTrackingParentDynamicObject(
    private val ownerProject: ProjectInternal,
    private val delegate: DynamicObject,
    private val referrerProject: ProjectInternal,
    private val problems: ProblemsListener,
    private val coupledProjectsListener: CoupledProjectsListener,
    private val problemFactory: ProblemFactory,
    private val dynamicCallProblemReporting: DynamicCallProblemReporting
) : DynamicObject {
    override fun hasMethod(name: String, vararg arguments: Any?): Boolean {
        val result = withLookupTracking { delegate.hasMethod(name, *arguments) }
        if (result.value) {
            onAccess(MemberKind.METHOD, name, result.isTopLevel)
        }
        return result.value
    }

    override fun tryInvokeMethod(name: String, vararg arguments: Any?): DynamicInvokeResult {
        // Use `hasMethod` to report access
        hasMethod(name, *arguments)
        return delegate.tryInvokeMethod(name, *arguments)
    }

    override fun hasProperty(name: String): Boolean {
        val result = withLookupTracking { delegate.hasProperty(name) }
        if (result.value) {
            onAccess(MemberKind.PROPERTY, name, result.isTopLevel)
        }
        return result.value
    }

    override fun tryGetProperty(name: String): DynamicInvokeResult {
        // Use `hasProperty` to report access
        hasProperty(name)
        return delegate.tryGetProperty(name)
    }

    override fun trySetProperty(name: String, value: Any?): DynamicInvokeResult {
        // Use `hasProperty` to report access
        hasProperty(name)
        return delegate.trySetProperty(name, value)
    }

    override fun trySetPropertyWithoutInstrumentation(name: String, value: Any?): DynamicInvokeResult {
        // Use `hasProperty` to report access
        hasProperty(name)
        return delegate.trySetPropertyWithoutInstrumentation(name, value)
    }

    override fun getProperties(): MutableMap<String, *> {
        // Directly use ACTIVE_LOOKUP here since we're checking before accessing properties
        onAccess(MemberKind.PROPERTY, null, ACTIVE_LOOKUP.get() == null)
        return withLookupTracking { delegate.properties }.value
    }

    override fun getMissingProperty(name: String): MissingPropertyException {
        return delegate.getMissingProperty(name)
    }

    override fun setMissingProperty(name: String): MissingPropertyException {
        return delegate.setMissingProperty(name)
    }

    override fun methodMissingException(name: String, vararg params: Any?): MissingMethodException {
        return delegate.methodMissingException(name, *params)
    }

    override fun getProperty(name: String): Any? {
        // Use `hasProperty` to report access
        hasProperty(name)
        return delegate.getProperty(name)
    }

    override fun setProperty(name: String, value: Any?) {
        // Use `hasProperty` to report access
        hasProperty(name)
        return delegate.setProperty(name, value)
    }

    override fun invokeMethod(name: String, vararg arguments: Any?): Any? {
        // Use `hasMethod` to report access
        hasMethod(name, *arguments)
        return delegate.invokeMethod(name, *arguments)
    }

    private
    enum class MemberKind {
        PROPERTY, METHOD
    }

    private
    fun onAccess(memberKind: MemberKind, memberName: String?, isTopLevelAccess: Boolean) {
        coupledProjectsListener.onProjectReference(referrerProject.owner, ownerProject.owner)
        if (isTopLevelAccess) {
            maybeReportProjectIsolationViolation(memberKind, memberName)
        }
    }

    @Suppress("ThrowingExceptionsWithoutMessageOrCause")
    private
    fun maybeReportProjectIsolationViolation(memberKind: MemberKind, memberName: String?) {
        if (dynamicCallProblemReporting.unreportedProblemInCurrentCall(PROBLEM_KEY)) {
            val problem = problemFactory.problem {
                text("Project ")
                reference(referrerProject.identityPath.toString())
                text(" cannot dynamically look up a ")
                text(memberKind.name.lowercase(Locale.ENGLISH))
                text(" in the parent project")
            }
                .mapLocation { location ->
                    when (memberKind) {
                        MemberKind.PROPERTY -> {
                            if (memberName != null)
                                PropertyTrace.Property(PropertyKind.PropertyUsage, memberName, PropertyTrace.Project(referrerProject.path, location))
                            else location
                        }

                        // method lookup is more clear from the stack trace, so keep the minimal trace pointing to the location:
                        MemberKind.METHOD -> location
                    }
                }
                .exception()
                .build()
            problems.onProblem(problem)
        }
    }

    companion object {
        val PROBLEM_KEY = Any()

        private val ACTIVE_LOOKUP = ThreadLocal<Unit>()

        private data class TrackingResult<T>(val value: T, val isTopLevel: Boolean)

        private fun <T : Any?> withLookupTracking(block: () -> T): TrackingResult<T> {
            val isActive = ACTIVE_LOOKUP.get()
            return if (isActive == null) {
                ACTIVE_LOOKUP.set(Unit)
                try {
                    TrackingResult(block(), true)
                } finally {
                    ACTIVE_LOOKUP.remove()
                }
            } else {
                TrackingResult(block(), false)
            }
        }
    }
}
