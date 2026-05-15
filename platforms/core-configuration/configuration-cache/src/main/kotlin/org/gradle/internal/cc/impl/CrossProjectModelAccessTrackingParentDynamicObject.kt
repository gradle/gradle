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
import org.gradle.api.internal.project.ProjectIdentity
import org.gradle.api.internal.project.ProjectState
import org.gradle.internal.configuration.problems.IsolatedProjectsProblemsReporter
import org.gradle.internal.configuration.problems.PropertyKind
import org.gradle.internal.configuration.problems.PropertyTrace.Project
import org.gradle.internal.configuration.problems.PropertyTrace.Property
import org.gradle.internal.metaobject.DynamicInvokeResult
import org.gradle.internal.metaobject.HierarchicalDynamicObject
import java.util.Locale.ENGLISH


internal
class CrossProjectModelAccessTrackingParentDynamicObject(
    private val parent: ProjectState,
    private val referrerProject: ProjectIdentity,
    private val ipProblems: IsolatedProjectsProblemsReporter,
    private val coupledProjectsListener: CoupledProjectsListener,
) : HierarchicalDynamicObject {

    private fun getMutableDelegate(): HierarchicalDynamicObject = parent.mutableModel.inheritedScope

    override fun getParent(): HierarchicalDynamicObject? {
        return getMutableDelegate().getParent()
    }

    override fun hasMethod(name: String, vararg arguments: Any?): Boolean {
        onAccess(MemberKind.METHOD, name)
        return ipProblems.runIgnoringProblemsOnCurrentThread {
            getMutableDelegate().hasMethod(name, *arguments)
        }
    }

    override fun tryInvokeMethod(name: String, vararg arguments: Any?): DynamicInvokeResult {
        onAccess(MemberKind.METHOD, name)
        return ipProblems.runIgnoringProblemsOnCurrentThread {
            getMutableDelegate().tryInvokeMethod(name, *arguments)
        }
    }

    override fun hasProperty(name: String): Boolean {
        onAccess(MemberKind.PROPERTY, name)
        return ipProblems.runIgnoringProblemsOnCurrentThread {
            getMutableDelegate().hasProperty(name)
        }
    }

    override fun tryGetProperty(name: String): DynamicInvokeResult {
        onAccess(MemberKind.PROPERTY, name)
        return ipProblems.runIgnoringProblemsOnCurrentThread {
            getMutableDelegate().tryGetProperty(name)
        }
    }

    override fun trySetProperty(name: String, value: Any?): DynamicInvokeResult {
        onAccess(MemberKind.PROPERTY, name)
        return ipProblems.runIgnoringProblemsOnCurrentThread {
            getMutableDelegate().trySetProperty(name, value)
        }
    }

    override fun trySetPropertyWithoutInstrumentation(name: String, value: Any?): DynamicInvokeResult {
        onAccess(MemberKind.PROPERTY, name)
        return ipProblems.runIgnoringProblemsOnCurrentThread {
            getMutableDelegate().trySetPropertyWithoutInstrumentation(name, value)
        }
    }

    override fun getProperties(): MutableMap<String, *> {
        onAccess(MemberKind.PROPERTY, null)
        return ipProblems.runIgnoringProblemsOnCurrentThread {
            getMutableDelegate().properties
        }
    }

    override fun getMissingProperty(name: String): MissingPropertyException {
        onAccess(MemberKind.PROPERTY, name)
        return ipProblems.runIgnoringProblemsOnCurrentThread {
            getMutableDelegate().getMissingProperty(name)
        }
    }

    override fun setMissingProperty(name: String): MissingPropertyException {
        onAccess(MemberKind.PROPERTY, name)
        return ipProblems.runIgnoringProblemsOnCurrentThread {
            getMutableDelegate().setMissingProperty(name)
        }
    }

    override fun methodMissingException(name: String, vararg params: Any?): MissingMethodException {
        onAccess(MemberKind.METHOD, name)
        return ipProblems.runIgnoringProblemsOnCurrentThread {
            getMutableDelegate().methodMissingException(name, *params)
        }
    }

    override fun getProperty(name: String): Any? {
        onAccess(MemberKind.PROPERTY, name)
        return ipProblems.runIgnoringProblemsOnCurrentThread {
            getMutableDelegate().getProperty(name)
        }
    }

    override fun setProperty(name: String, value: Any?) {
        onAccess(MemberKind.PROPERTY, name)
        return ipProblems.runIgnoringProblemsOnCurrentThread {
            getMutableDelegate().setProperty(name, value)
        }
    }

    override fun invokeMethod(name: String, vararg arguments: Any?): Any? {
        onAccess(MemberKind.METHOD, name)
        return ipProblems.runIgnoringProblemsOnCurrentThread {
            getMutableDelegate().invokeMethod(name, *arguments)
        }
    }

    override fun getDisplayName(): String {
        return getMutableDelegate().displayName
    }

    private
    enum class MemberKind {
        PROPERTY, METHOD
    }

    @Suppress("ThrowingExceptionsWithoutMessageOrCause") // false-positive on the `.exception()` call
    private
    fun onAccess(memberKind: MemberKind, memberName: String?) {
        coupledProjectsListener.onProjectReference(referrerProject, this.parent.identity)

        ipProblems.report {
            problem {
                text("Project ")
                reference(referrerProject.buildTreePath)
                text(" cannot dynamically look up a ")
                text(memberKind.name.lowercase(ENGLISH))
                text(" in the parent project ")
                reference(parent.identityPath)
            }
                .mapLocation { location ->
                    when (memberKind) {
                        MemberKind.PROPERTY -> {
                            if (memberName != null)
                                Property(PropertyKind.PropertyUsage, memberName, Project(referrerProject.projectPath.asString(), location))
                            else location
                        }

                        // method lookup is more clear from the stack trace, so keep the minimal trace pointing to the location:
                        MemberKind.METHOD -> location
                    }
                }
                .exception()
                .build()
        }
    }
}
