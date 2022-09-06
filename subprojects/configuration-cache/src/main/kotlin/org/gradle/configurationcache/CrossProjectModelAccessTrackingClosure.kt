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

package org.gradle.configurationcache

import groovy.lang.Closure
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.project.CrossProjectModelAccess
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.execution.taskgraph.TaskExecutionGraphInternal
import java.util.Objects


class CrossProjectModelAccessTrackingClosure<T>(
    private val delegate: Closure<T>,
    private val referrerProject: ProjectInternal,
    private val crossProjectModelAccess: CrossProjectModelAccess
) : Closure<T>(
    trackingProjectAccess(crossProjectModelAccess, referrerProject, delegate.owner),
    trackingProjectAccess(crossProjectModelAccess, referrerProject, delegate.thisObject)
) {
    @Suppress("unused")
    fun doCall(vararg args: Any) {
        val numClosureArgs = delegate.maximumNumberOfParameters
        val finalArgs: Array<out Any> = args.take(numClosureArgs).map { trackingProjectAccess(crossProjectModelAccess, referrerProject, it) }.toTypedArray()
        delegate.call(*finalArgs)
    }

    override fun setDelegate(delegateObject: Any) {
        delegate.delegate = trackingProjectAccess(crossProjectModelAccess, referrerProject, delegateObject)
    }

    override fun setResolveStrategy(resolveStrategy: Int) {
        delegate.resolveStrategy = resolveStrategy
    }

    override fun getMaximumNumberOfParameters(): Int {
        return delegate.maximumNumberOfParameters
    }

    companion object {
        private
        fun trackingProjectAccess(crossProjectModelAccess: CrossProjectModelAccess, referrerProject: ProjectInternal, modelObject: Any): Any =
            when (modelObject) {
                is ProjectInternal -> crossProjectModelAccess.access(referrerProject, modelObject)
                is GradleInternal -> crossProjectModelAccess.gradleInstanceForProject(referrerProject, modelObject)
                is TaskExecutionGraphInternal -> crossProjectModelAccess.taskGraphForProject(referrerProject, modelObject)
                else -> modelObject
            }
    }

    override fun equals(other: Any?): Boolean =
        javaClass == (other as? CrossProjectModelAccessTrackingClosure<*>)?.javaClass &&
            other.delegate == delegate &&
            other.referrerProject == referrerProject

    override fun hashCode(): Int = Objects.hash(delegate, referrerProject)

    override fun toString(): String = "CrossProjectModelAccessTrackingClosure($delegate, $referrerProject)"
}
