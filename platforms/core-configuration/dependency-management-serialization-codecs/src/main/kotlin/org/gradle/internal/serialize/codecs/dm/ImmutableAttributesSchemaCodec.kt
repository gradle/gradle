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

package org.gradle.internal.serialize.codecs.dm

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import org.gradle.api.Action
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.CompatibilityCheckDetails
import org.gradle.api.attributes.MultipleCandidatesDetails
import org.gradle.api.internal.attributes.DefaultCompatibilityRuleChain
import org.gradle.api.internal.attributes.DefaultDisambiguationRuleChain
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema
import org.gradle.internal.action.ConfigurableRule
import org.gradle.internal.action.ConfigurableRules
import org.gradle.internal.action.DefaultConfigurableRule
import org.gradle.internal.action.DefaultConfigurableRules
import org.gradle.internal.action.InstantiatingAction
import org.gradle.internal.extensions.stdlib.uncheckedCast
import org.gradle.internal.isolation.Isolatable
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.decodePreservingSharedIdentity
import org.gradle.internal.serialize.graph.encodePreservingSharedIdentityOf
import org.gradle.internal.serialize.graph.readList
import org.gradle.internal.serialize.graph.readNonNull
import org.gradle.internal.serialize.graph.writeCollection

class ImmutableAttributesSchemaCodec(
    private val instantiator: Instantiator
) : Codec<ImmutableAttributesSchema> {

    override suspend fun WriteContext.encode(value: ImmutableAttributesSchema) {
        // Attributes schemas are interned. See `ImmutableAttributesSchemaFactory`
        encodePreservingSharedIdentityOf(value) {
            writeSchema(it)
        }
    }

    override suspend fun ReadContext.decode(): ImmutableAttributesSchema {
        return decodePreservingSharedIdentity {
            readSchema(instantiator)
        }
    }
}

const val INSTANTIATING_ACTION_TYPE = 0
const val OPAQUE_ACTION_TYPE = 1


private
suspend fun WriteContext.writeSchema(schema: ImmutableAttributesSchema) {
    writeCollection(schema.attributes) { attribute ->
        writeAttribute(attribute)
        val value = schema.getStrategy(attribute)!!
        writeStrategy(value)
    }
    writeCollection(schema.attributeDisambiguationPrecedence) {
        writeAttribute(it)
    }
}

private
suspend fun <T> WriteContext.writeStrategy(value: ImmutableAttributesSchema.ImmutableAttributeMatchingStrategy<T>) {
    writeCollection(value.compatibilityRules) {
        when (it) {
            is InstantiatingAction -> {
                writeSmallInt(INSTANTIATING_ACTION_TYPE)
                writeInstantiatingAction(it)
            }
            else -> {
                writeSmallInt(OPAQUE_ACTION_TYPE)
                write(it)
            }
        }
    }
    writeCollection(value.disambiguationRules) {
        when (it) {
            is InstantiatingAction -> {
                writeSmallInt(INSTANTIATING_ACTION_TYPE)
                writeInstantiatingAction(it)
            }
            else -> {
                writeSmallInt(OPAQUE_ACTION_TYPE)
                write(it)
            }
        }
    }
}


private
suspend fun WriteContext.writeInstantiatingAction(action: InstantiatingAction<*>) {
    val rules = action.rules.configurableRules
    assert(rules.size == 1)
    val rule = rules[0]

    writeClass(rule.ruleClass)
    write(rule.ruleParams)
}


private
fun WriteContext.writeAttribute(attribute: Attribute<*>) {
    writeString(attribute.name)
    writeClass(attribute.type)
}


private
suspend fun ReadContext.readSchema(instantiator: Instantiator): ImmutableAttributesSchema {
    val strategies = readList {
        val attribute = readAttribute()
        readStrategy(attribute, instantiator)
    }.associate { it }

    val precedence = readList {
        readAttribute()
    }

    return ImmutableAttributesSchema(
        ImmutableMap.copyOf(strategies),
        ImmutableList.copyOf(precedence)
    )
}


private suspend fun <T> ReadContext.readStrategy(
    attribute: Attribute<T>,
    instantiator: Instantiator
): Pair<Attribute<T>, ImmutableAttributesSchema.ImmutableAttributeMatchingStrategy<T>> {
    val compatibilityRules: List<Action<in CompatibilityCheckDetails<T>>> = readList {
        when (readSmallInt()) {
            INSTANTIATING_ACTION_TYPE -> readCompatibilityInstantiatingAction(instantiator)
            OPAQUE_ACTION_TYPE -> read()!!.uncheckedCast()
            else -> error("Unknown action type")
        }
    }
    val disambiguationRules: List<Action<in MultipleCandidatesDetails<T>>> = readList {
        when (readSmallInt()) {
            INSTANTIATING_ACTION_TYPE -> readDisambiguationInstantiatingAction(instantiator)
            OPAQUE_ACTION_TYPE -> read()!!.uncheckedCast()
            else -> error("Unknown action type")
        }
    }
    val strategy = ImmutableAttributesSchema.ImmutableAttributeMatchingStrategy(
        ImmutableList.copyOf(compatibilityRules),
        ImmutableList.copyOf(disambiguationRules)
    )
    return attribute to strategy
}


private
suspend fun <T> ReadContext.readCompatibilityInstantiatingAction(instantiator: Instantiator): Action<in CompatibilityCheckDetails<T>> {
    val rule = readConfigurableRule<CompatibilityCheckDetails<T>>()
    val rules: ConfigurableRules<CompatibilityCheckDetails<T>> = DefaultConfigurableRules.of(rule)
    return InstantiatingAction(
        rules,
        instantiator,
        DefaultCompatibilityRuleChain.ExceptionHandler(rule.ruleClass)
    )
}


private
suspend fun <T> ReadContext.readDisambiguationInstantiatingAction(instantiator: Instantiator): Action<in MultipleCandidatesDetails<T>> {
    val rule = readConfigurableRule<MultipleCandidatesDetails<T>>()
    val rules: ConfigurableRules<MultipleCandidatesDetails<T>> = DefaultConfigurableRules.of(rule)
    return InstantiatingAction(
        rules,
        instantiator,
        DefaultDisambiguationRuleChain.ExceptionHandler(rule.ruleClass)
    )
}


private suspend fun <T> ReadContext.readConfigurableRule(): ConfigurableRule<T> {
    val ruleClass = readClass().uncheckedCast<Class<Action<T>>>()
    val ruleParams = readNonNull<Isolatable<Array<Any>>>()
    return DefaultConfigurableRule.ofIsolatable(ruleClass, ruleParams)
}


private
fun ReadContext.readAttribute(): Attribute<Any> {
    val name = readString()
    val type = readClass()
    return Attribute.of(name, type.uncheckedCast())
}
