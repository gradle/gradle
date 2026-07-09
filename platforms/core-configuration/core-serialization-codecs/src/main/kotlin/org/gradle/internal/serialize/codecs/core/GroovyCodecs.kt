/*
 * Copyright 2024 the original author or authors.
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

import groovy.lang.Binding
import groovy.lang.Closure
import groovy.lang.GroovyObjectSupport
import groovy.lang.MetaClass
import org.codehaus.groovy.runtime.InvokerHelper
import org.gradle.api.InvalidUserCodeException
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.api.internal.DynamicObjectAware
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.SettingsInternal
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.invocation.Gradle
import org.gradle.groovy.scripts.BasicScript
import org.gradle.groovy.scripts.BasicScriptConfigurationCacheOperations
import org.gradle.internal.configuration.problems.DocumentationSection.RequirementsGradleModelTypes
import org.gradle.internal.configuration.problems.ProblemFactory
import org.gradle.internal.configuration.problems.ProblemsListener
import org.gradle.internal.configuration.problems.PropertyTrace
import org.gradle.internal.metaobject.AbstractDynamicObject
import org.gradle.internal.metaobject.ConfigureDelegate
import org.gradle.internal.metaobject.DynamicInvokeResult
import org.gradle.internal.metaobject.DynamicObject
import org.gradle.internal.reflection.access.ObjectOpener
import org.gradle.internal.serialize.beans.services.relevantStateOf
import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.codecs.BindingsBuilder
import org.gradle.internal.serialize.graph.decodeBean
import org.gradle.internal.serialize.graph.decodePreservingIdentity
import org.gradle.internal.serialize.graph.encodeBean
import org.gradle.internal.serialize.graph.encodePreservingIdentityOf
import org.gradle.internal.serialize.graph.readEnum
import org.gradle.internal.serialize.graph.serviceOf
import org.gradle.internal.serialize.graph.writeEnum
import org.gradle.internal.service.ServiceRegistry
import java.lang.reflect.Field
import java.lang.reflect.Proxy


fun BindingsBuilder.groovyCodecs(objectOpener: ObjectOpener) {
    bind(ClosureCodec)
    bind(GroovyScriptCodec(objectOpener))
    bind(GroovyMetaClassCodec)
}


internal
object ClosureCodec : Codec<Closure<*>> {
    override suspend fun WriteContext.encode(value: Closure<*>) {
        // Keep the owning script so a closure can still call script-defined methods after cache reuse;
        // the script's access to the build model is severed on read (see GroovyScriptCodec).
        // The delegate is discarded — it is replaced by the caller.
        write(findOwningScript(value))
        write(findOwningScript(value.thisObject))
        encodeBean(value.dehydrate())
    }

    override suspend fun ReadContext.decode(): Closure<*> {
        val owner = read() ?: BrokenObject
        val thisObject = read() ?: BrokenObject

        return (decodeBean() as Closure<*>).rehydrate(null, owner, thisObject)
    }

    /**
     * Travels up the 'owner' chain of a closure to locate the script that the closure belongs to, if any
     */
    private
    fun findOwningScript(value: Any?): Any? {
        return when (value) {
            is org.gradle.api.Script -> value
            is ConfigureDelegate -> value._original_owner()?.let { findOwningScript(it) }
            is Closure<*> -> value.owner?.let { findOwningScript(it) }
            else -> null
        }
    }

    /**
     * Stand-in owner for closures that do not belong to a script. It has no members, so any lookup
     * falls through to the closure's delegate — matching the pre-scrubbing behavior.
     */
    internal
    object BrokenObject : GroovyObjectSupport()
}


/**
 * Serializes a Groovy build/settings/init script by keeping the real script instance so that
 * closures which call script-defined methods keep working across the configuration cache (#20126).
 *
 * Only the script class and its target kind are written; on read a fresh instance of the same
 * compiled class is created (its no-arg constructor sets up the dynamic-object plumbing without
 * running the script body) and its target is replaced with a [BrokenModelDynamicObject]. So a
 * closure's call to a script method resolves on the real script, while any access to the build
 * model from that method fails with a clear problem instead of a raw missing-method error.
 *
 * Identity is preserved, so the many closures that share one owning script decode to a single
 * scrubbed instance.
 *
 * Implements [#20126](https://github.com/gradle/gradle/issues/20126), reusing the #22879 approach.
 */
internal
class GroovyScriptCodec(private val objectOpener: ObjectOpener) : Codec<BasicScript> {

    override suspend fun WriteContext.encode(value: BasicScript) {
        encodePreservingIdentityOf(value) {
            writeClass(value.javaClass)
            writeEnum(targetKindOf(value))
            // Serialize every field EXCEPT the ones we intentionally omit (see isOmittedField); each
            // retained value rides its own codec (a ScriptFileOperations via ScriptFileOperationsCodec,
            // any service via ServicesCodec, ...), so the script's state keeps working after cache reuse.
            val fields = retainedFieldsOf(value)
            writeSmallInt(fields.size)
            for (field in fields) {
                writeClass(field.declaringClass)
                writeString(field.name)
                write(field.get(value))
            }
        }
    }

    override suspend fun ReadContext.decode(): BasicScript {
        val problemFactory = isolate.owner.serviceOf<ProblemFactory>()
        val listener = problemsListener
        val propertyTrace = trace
        val ownerServices = isolate.owner.serviceOf<ServiceRegistry>()
        return decodePreservingIdentity { id ->
            val scriptType = readClass()
            val targetKind = readEnum<GroovyScriptTargetKind>()
            val script = scriptType.getDeclaredConstructor().newInstance() as BasicScript
            isolate.identities.putInstance(id, script)
            val count = readSmallInt()
            repeat(count) {
                val declaringClass = readClass()
                val name = readString()
                val fieldValue = read()
                declaringClass.getDeclaredField(name).apply { isAccessible = true }.set(script, fieldValue)
            }
            // The script's own ServiceRegistry can't ride a codec; hand it the isolate owner's.
            for (field in serviceRegistryFieldsOf(scriptType)) {
                field.set(script, ownerServices)
            }
            // Replace the target so build-model access from a closure fails gracefully.
            BasicScriptConfigurationCacheOperations.installBrokenTarget(
                script,
                brokenModelTarget(targetKind, propertyTrace, problemFactory, listener)
            )
            script
        }
    }

    /**
     * A pure broken stand-in for the severed build model. It IS-A `ProjectInternal`/`SettingsInternal`/
     * `GradleInternal`, so `ProjectScript`'s `(ProjectInternal) getScriptTarget()` cast (and the
     * settings/init equivalents) keeps working — but every model method reports the configuration-cache
     * problem. Its `DynamicObjectAware` view returns the [BrokenModelDynamicObject], which drives the
     * Groovy MOP: a closure's call to a script member still resolves on the retained script, while a
     * model member reports the problem.
     *
     * The script's own project-independent services (`logging`/`logger`/output capture) are NOT routed
     * here — they are materialized onto the script's own fields (see `ProjectScript`), so this stand-in
     * can stay purely broken.
     */
    private
    fun brokenModelTarget(
        targetKind: GroovyScriptTargetKind,
        trace: PropertyTrace,
        problemFactory: ProblemFactory,
        listener: ProblemsListener
    ): Any {
        val brokenDynamicObject = BrokenModelDynamicObject(targetKind.modelType, trace, problemFactory, listener)
        val internalType = targetKind.internalModelType
        return Proxy.newProxyInstance(internalType.classLoader, arrayOf(internalType, DynamicObjectAware::class.java)) { _, method, _ ->
            if (method.name == "getAsDynamicObject" && method.parameterCount == 0) {
                brokenDynamicObject
            } else {
                groovyScriptReferenced(method.name, trace, problemFactory, listener)
            }
        }
    }


    private
    fun targetKindOf(value: BasicScript): GroovyScriptTargetKind =
        when (value.scriptTarget) {
            is Settings -> GroovyScriptTargetKind.Settings
            is Gradle -> GroovyScriptTargetKind.Init
            else -> GroovyScriptTargetKind.Project
        }

    /**
     * The script's fields to carry across the cache: everything except the ones we intentionally omit
     * (see [isOmittedField]). Null-valued fields are skipped — there is nothing to serialize.
     */
    private
    fun retainedFieldsOf(script: BasicScript): List<Field> =
        relevantStateOf(script.javaClass, objectOpener)
            .map { it.field }
            .filter { field -> !isOmittedField(field, field.get(script)) }

    /**
     * The fields we deliberately do NOT serialize:
     *  - the build-model target (its value is a `Project`/`Settings`/`Gradle`) — severed and replaced
     *    with a broken stand-in on decode;
     *  - the script's dynamic-object plumbing and Groovy scaffolding (`DynamicObject`, `Binding`,
     *    `MetaClass`) — rebuilt by the no-arg constructor;
     *  - the `ServiceRegistry` — re-resolved from the isolate owner (it has no serialized form);
     *  - any `ClassLoader` — not serializable and not needed by a stored closure.
     *
     * Everything else is retained and round-trips through its own codec.
     */
    private
    fun isOmittedField(field: Field, value: Any?): Boolean =
        value == null ||
            isBuildModelType(value.javaClass) ||
            DynamicObject::class.java.isAssignableFrom(field.type) ||
            Binding::class.java.isAssignableFrom(field.type) ||
            MetaClass::class.java.isAssignableFrom(field.type) ||
            ClassLoader::class.java.isAssignableFrom(field.type) ||
            ServiceRegistry::class.java.isAssignableFrom(field.type)

    private
    fun isBuildModelType(type: Class<*>): Boolean =
        Project::class.java.isAssignableFrom(type) ||
            Settings::class.java.isAssignableFrom(type) ||
            Gradle::class.java.isAssignableFrom(type)

    private
    fun serviceRegistryFieldsOf(scriptType: Class<*>): List<Field> =
        relevantStateOf(scriptType, objectOpener)
            .map { it.field }
            .filter { ServiceRegistry::class.java.isAssignableFrom(it.type) }
}


private
enum class GroovyScriptTargetKind(val modelType: Class<*>, val internalModelType: Class<*>) {
    Project(org.gradle.api.Project::class.java, ProjectInternal::class.java),
    Settings(org.gradle.api.initialization.Settings::class.java, SettingsInternal::class.java),
    Init(org.gradle.api.invocation.Gradle::class.java, GradleInternal::class.java)
}


/**
 * Reports (and throws) the configuration-cache problem for a Groovy closure reaching a Gradle script
 * object (the build model) at execution time.
 */
private
fun groovyScriptReferenced(
    invocationDescription: String,
    trace: PropertyTrace,
    problemFactory: ProblemFactory,
    problemsListener: ProblemsListener
): Nothing {
    val exceptionMessage =
        "Invocation of '$invocationDescription' references a Gradle script object from a Groovy closure at execution time, which is unsupported with the configuration cache."

    val problem = problemFactory.problem {
        text("invocation of ")
        reference(invocationDescription)
        text(" references a Gradle script object from a Groovy closure at execution time, which is unsupported with the configuration cache.")
    }
        .exception(exceptionMessage)
        .documentationSection(RequirementsGradleModelTypes)
        .mapLocation { trace }
        .build()

    problemsListener.onExecutionTimeProblem(problem)

    // We normally fail immediately on execution-time problems, except when in the warning mode.
    // However, even in the warning mode, we don't have a reasonable way of proceeding in this situation
    // so we make sure to throw
    throw problem.exception ?: InvalidUserCodeException(exceptionMessage)
}


/**
 * Stands in for a scrubbed Groovy script's target (the `Project`/`Settings`/`Gradle`). Members that
 * belong to the model type report a clear configuration-cache problem when touched; anything else is
 * reported as "not found" so that a closure's owner-first resolution can fall through to its
 * delegate, exactly as the previous `BrokenScript` owner did.
 */
private
class BrokenModelDynamicObject(
    private val modelType: Class<*>,
    private val trace: PropertyTrace,
    private val problemFactory: ProblemFactory,
    private val problemsListener: ProblemsListener
) : AbstractDynamicObject() {

    private
    val targetMetadata = ThreadSafeMetaClassWrapper(modelType)

    override fun getDisplayName(): String = "broken ${modelType.simpleName} reference"

    override fun hasProperty(name: String): Boolean = targetMetadata.hasProperty(null, name) != null

    override fun tryGetProperty(name: String): DynamicInvokeResult =
        if (targetMetadata.hasProperty(null, name) != null) scriptReferenced(name) else DynamicInvokeResult.notFound()

    override fun trySetProperty(name: String, value: Any?): DynamicInvokeResult =
        if (targetMetadata.hasProperty(null, name) != null) scriptReferenced(name) else DynamicInvokeResult.notFound()

    override fun tryInvokeMethod(name: String, vararg arguments: Any?): DynamicInvokeResult =
        if (targetMetadata.respondsTo(null, name).isNotEmpty()) scriptReferenced(name) else DynamicInvokeResult.notFound()

    private
    fun scriptReferenced(invocationDescription: String): Nothing =
        groovyScriptReferenced(invocationDescription, trace, problemFactory, problemsListener)
}


internal
object GroovyMetaClassCodec : Codec<MetaClass> {
    override suspend fun WriteContext.encode(value: MetaClass) {
        writeClass(value.theClass)
    }

    override suspend fun ReadContext.decode(): MetaClass? {
        return InvokerHelper.getMetaClass(readClass())
    }
}


/**
 * MetaClass implementations in Groovy (at least, in Groovy 4) are not fully thread-safe.
 * This wrapper adds the necessary level of thread-safety for concurrent property lookups.
 *
 * This can be removed after updating to a thread-safe version of Groovy runtime.
 */
@JvmInline
private value class ThreadSafeMetaClassWrapper private constructor(
    private val metaClass: MetaClass
) {
    constructor(cls: Class<*>) : this(synchronized(cls) { InvokerHelper.getMetaClass(cls) })

    fun hasProperty(obj: Any?, propertyName: String) = synchronized(metaClass) { metaClass.hasProperty(obj, propertyName) }

    fun respondsTo(obj: Any?, name: String) = synchronized(metaClass) { metaClass.respondsTo(obj, name) }
}
