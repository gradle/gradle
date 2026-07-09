/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.serialize.codecs.core

import org.gradle.api.InvalidUserCodeException
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.api.invocation.Gradle
import org.gradle.internal.configuration.problems.DocumentationSection.RequirementsGradleModelTypes
import org.gradle.internal.configuration.problems.ProblemFactory
import org.gradle.internal.configuration.problems.ProblemsListener
import org.gradle.internal.configuration.problems.PropertyTrace
import org.gradle.internal.reflection.access.ObjectOpener
import org.gradle.internal.scripts.ScrubbableScript
import org.gradle.internal.serialize.beans.services.relevantStateOf
import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.decodePreservingIdentity
import org.gradle.internal.serialize.graph.encodePreservingIdentityOf
import org.gradle.internal.serialize.graph.serviceOf
import java.lang.reflect.Field
import java.lang.reflect.Proxy


/**
 * Serializes a compiled script instance by keeping its Project-independent state and dropping only
 * the genuine bridge to the build model.
 *
 * Implements [#22879](https://github.com/gradle/gradle/issues/22879).
 *
 * We serialize every instance field across the whole hierarchy EXCEPT:
 *  - build-model references (`Project`/`Settings`/`Gradle`, e.g. the `$$implicitReceiver_*`) —
 *    replaced on decode with broken dynamic proxies that report a configuration-cache problem when
 *    touched, mirroring the Groovy `BrokenScript`;
 *  - the script `host` (`KotlinScriptHost`, holds the live model + service registry) and the
 *    script result (`$$result`) and other `$$`-prefixed codegen metadata — dropped (left `null`).
 *
 * Everything else is serialized as-is, which crucially includes user-declared `val`/`var`/`by lazy`
 * fields AND the base-class service delegates (`logger`/`logging`/`fileOperations`/`resources`).
 * Those delegates are `unsafeLazy` (a `Serializable` `kotlin.Lazy`): serializing an unforced one runs
 * its `writeReplace`, which forces the value and drops the initializer — so the live host that the
 * initializer read from never enters the stream, and only the resolved service value does. Each such
 * value then rides its own codec: [org.gradle.api.logging.Logger] via its codec; the script's
 * [org.gradle.api.internal.file.FileOperations] via [ScriptFileOperationsCodec], which preserves its
 * base dir; the logging manager via `ServicesCodec`. So `file(...)`, `logger`, etc. keep working at
 * execution.
 *
 * On decode the instance is allocated without running its constructor (via the same
 * constructor-for-serialization path used by ordinary beans), so we never need a live host.
 * Identity is registered before the fields are read, so a captured value that references the script
 * back (e.g. a `by lazy` initializer) resolves to this same instance.
 */
class ScrubbableScriptCodec(private val objectOpener: ObjectOpener) : Codec<ScrubbableScript> {

    override suspend fun WriteContext.encode(value: ScrubbableScript) {
        encodePreservingIdentityOf(value) {
            val scriptType = value.javaClass
            writeClass(scriptType)
            val fields = serializableFieldsOf(scriptType)
            writeSmallInt(fields.size)
            for (field in fields) {
                writeClass(field.declaringClass)
                writeString(field.name)
                write(field.get(value))
            }
        }
    }

    override suspend fun ReadContext.decode(): ScrubbableScript {
        val problemFactory = isolate.owner.serviceOf<ProblemFactory>()
        val listener = problemsListener
        val propertyTrace = trace
        return decodePreservingIdentity { id ->
            val scriptType = readClass()
            val script = beanStateReaderFor(scriptType).run { newBeanWithId(id) }
            val count = readSmallInt()
            repeat(count) {
                val declaringClass = readClass()
                val name = readString()
                val fieldValue = read()
                declaringClass.getDeclaredField(name).apply { isAccessible = true }.set(script, fieldValue)
            }
            installBrokenReferences(script, scriptType, propertyTrace, problemFactory, listener)
            script as ScrubbableScript
        }
    }

    /**
     * Fields to serialize: every non-static instance field across the hierarchy that is not dropped.
     */
    private
    fun serializableFieldsOf(scriptType: Class<*>): List<Field> =
        relevantStateOf(scriptType, objectOpener)
            .map { it.field }
            .filter { !isDroppedField(it) }

    /**
     * A field is dropped (not serialized) when it is a build-model reference (`Project`/`Settings`/
     * `Gradle`), a [ScrubbableScript.ScrubbedOut] type, or a synthetic script field the Kotlin compiler
     * emits — the `$$implicitReceiver_*` receivers and the `$$result` script value.
     *
     * The base classes delegate `PluginAware` (`PluginAware by ...`), which the compiler backs with a
     * `$$delegate_N` field. For a project script that field is typed `Project`, so it's already caught
     * as a build-model reference; for settings/init it's a `PluginAwareScript`, which is marked
     * [ScrubbableScript.ScrubbedOut] so it's dropped by type rather than by a `$$delegate_N` name match.
     */
    private
    fun isDroppedField(field: Field): Boolean =
        isBuildModelType(field.type) ||
            ScrubbableScript.ScrubbedOut::class.java.isAssignableFrom(field.type) ||
            field.name.startsWith($$$"$$implicitReceiver_") ||
            field.name == $$$"$$result"

    /**
     * Design constraint (#22879): no code reachable from a script should hit a raw
     * `NullPointerException` at execution time because of a dropped field. So every dropped field
     * whose type is an interface is replaced with a broken dynamic proxy that reports a clear
     * configuration-cache problem when touched.
     *
     * Dropped fields of a concrete (non-proxyable) type — such as the settings/init `PluginAware`
     * delegate — are left `null`. Those are not reachable from a task action at execution time.
     * (The script `host` is an interface, so it gets a proxy: `buildscript`/`initscript` accessed
     * from a task action then fail with a clear problem rather than a `NullPointerException`.)
     */
    private
    fun installBrokenReferences(
        script: Any,
        scriptType: Class<*>,
        trace: PropertyTrace,
        problemFactory: ProblemFactory,
        listener: ProblemsListener
    ) {
        for (field in relevantStateOf(scriptType, objectOpener).map { it.field }) {
            val fieldType = field.type
            if (!isDroppedField(field) || !fieldType.isInterface) continue
            field.isAccessible = true
            field.set(script, brokenModelProxy(fieldType, trace, problemFactory, listener))
        }
    }

    private
    fun isBuildModelType(type: Class<*>): Boolean =
        Project::class.java.isAssignableFrom(type) ||
            Settings::class.java.isAssignableFrom(type) ||
            Gradle::class.java.isAssignableFrom(type)

    private
    fun brokenModelProxy(
        fieldType: Class<*>,
        trace: PropertyTrace,
        problemFactory: ProblemFactory,
        listener: ProblemsListener
    ): Any {
        val modelTypeName = modelTypeNameOf(fieldType)
        return Proxy.newProxyInstance(fieldType.classLoader, arrayOf(fieldType)) { _, method, _ ->
            // Every invocation — including toString/hashCode/equals — reports the problem, so that even
            // `println(capturedProject)` or using it as a map key fails clearly rather than silently
            // yielding a benign stand-in value. Matches the Groovy broken target.
            modelReferenced(method.name, modelTypeName, trace, problemFactory, listener)
        }
    }

    private
    fun modelTypeNameOf(type: Class<*>): String = when {
        Project::class.java.isAssignableFrom(type) -> "Project"
        Settings::class.java.isAssignableFrom(type) -> "Settings"
        Gradle::class.java.isAssignableFrom(type) -> "Gradle"
        else -> type.simpleName
    }

    private
    fun modelReferenced(
        invocationDescription: String,
        modelTypeName: String,
        trace: PropertyTrace,
        problemFactory: ProblemFactory,
        listener: ProblemsListener
    ): Nothing {
        val exceptionMessage =
            "Invocation of '$invocationDescription' references a $modelTypeName object from a Kotlin script lambda at execution time, which is unsupported with the configuration cache."

        val problem = problemFactory.problem {
            text("invocation of ")
            reference(invocationDescription)
            text(" references a $modelTypeName object from a Kotlin script lambda at execution time, which is unsupported with the configuration cache.")
        }
            .exception(exceptionMessage)
            .documentationSection(RequirementsGradleModelTypes)
            .mapLocation { trace }
            .build()

        listener.onExecutionTimeProblem(problem)

        throw problem.exception ?: InvalidUserCodeException(exceptionMessage)
    }
}
