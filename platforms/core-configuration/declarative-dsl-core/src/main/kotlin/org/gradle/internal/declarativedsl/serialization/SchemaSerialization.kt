/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.declarativedsl.serialization

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.gradle.declarative.dsl.schema.AnalysisSchema
import org.gradle.declarative.dsl.schema.ConfigureAccessor
import org.gradle.declarative.dsl.schema.DataClass
import org.gradle.declarative.dsl.schema.DataParameter
import org.gradle.declarative.dsl.schema.DataProperty
import org.gradle.declarative.dsl.schema.DataType
import org.gradle.declarative.dsl.schema.DataTypeRef
import org.gradle.declarative.dsl.schema.FqName
import org.gradle.declarative.dsl.schema.FunctionSemantics
import org.gradle.declarative.dsl.schema.ParameterSemantics
import org.gradle.declarative.dsl.schema.SchemaFunction
import org.gradle.declarative.dsl.schema.SchemaMemberFunction
import org.gradle.internal.declarativedsl.analysis.ConfigureAccessorInternal
import org.gradle.internal.declarativedsl.analysis.DataTypeRefInternal
import org.gradle.internal.declarativedsl.analysis.DefaultAnalysisSchema
import org.gradle.internal.declarativedsl.analysis.DefaultDataBuilderFunction
import org.gradle.internal.declarativedsl.analysis.DefaultDataClass
import org.gradle.internal.declarativedsl.analysis.DefaultDataConstructor
import org.gradle.internal.declarativedsl.analysis.DefaultDataMemberFunction
import org.gradle.internal.declarativedsl.analysis.DefaultDataParameter
import org.gradle.internal.declarativedsl.analysis.DefaultDataProperty
import org.gradle.internal.declarativedsl.analysis.DefaultDataTopLevelFunction
import org.gradle.internal.declarativedsl.analysis.DefaultFqName
import org.gradle.internal.declarativedsl.analysis.FunctionSemanticsInternal
import org.gradle.internal.declarativedsl.analysis.ParameterSemanticsInternal
import org.gradle.internal.declarativedsl.language.DataTypeInternal


object SchemaSerialization {

    private
    val json = Json {
        serializersModule = SerializersModule {
            polymorphic(ConfigureAccessor::class) {
                subclass(ConfigureAccessorInternal.DefaultConfiguringLambdaArgument::class)
                subclass(ConfigureAccessorInternal.DefaultCustom::class)
                subclass(ConfigureAccessorInternal.DefaultProperty::class)
            }
            polymorphic(DataType::class) {
                subclass(DataTypeInternal.DefaultIntDataType::class)
                subclass(DataTypeInternal.DefaultLongDataType::class)
                subclass(DataTypeInternal.DefaultStringDataType::class)
                subclass(DataTypeInternal.DefaultBooleanDataType::class)
                subclass(DataTypeInternal.DefaultNullType::class)
                subclass(DataTypeInternal.DefaultUnitType::class)
                polymorphic(DataClass::class) {
                    subclass(DefaultDataClass::class)
                }
            }
            polymorphic(DataTypeRef::class) {
                subclass(DataTypeRefInternal.DefaultName::class)
                subclass(DataTypeRefInternal.DefaultType::class)
            }
            polymorphic(DataParameter::class) {
                subclass(DefaultDataParameter::class)
            }
            polymorphic(DataProperty::class) {
                subclass(DefaultDataProperty::class)
            }
            polymorphic(DataProperty.PropertyMode::class) {
                subclass(DefaultDataProperty.DefaultPropertyMode.DefaultReadWrite::class)
                subclass(DefaultDataProperty.DefaultPropertyMode.DefaultReadOnly::class)
                subclass(DefaultDataProperty.DefaultPropertyMode.DefaultWriteOnly::class)
            }
            polymorphic(FqName::class) {
                subclass(DefaultFqName::class)
            }
            polymorphic(FunctionSemantics::class) {
                subclass(FunctionSemanticsInternal.DefaultAccessAndConfigure::class)
                subclass(FunctionSemanticsInternal.DefaultAddAndConfigure::class)
                subclass(FunctionSemanticsInternal.DefaultBuilder::class)
                subclass(FunctionSemanticsInternal.DefaultPure::class)
            }
            polymorphic(FunctionSemantics.AccessAndConfigure.ReturnType::class) {
                subclass(FunctionSemanticsInternal.DefaultAccessAndConfigure.DefaultReturnType.DefaultUnit::class)
                subclass(FunctionSemanticsInternal.DefaultAccessAndConfigure.DefaultReturnType.DefaultConfiguredObject::class)
            }
            polymorphic(FunctionSemantics.Builder::class) {
                subclass(FunctionSemanticsInternal.DefaultBuilder::class)
            }
            polymorphic(FunctionSemantics.ConfigureSemantics.ConfigureBlockRequirement::class) {
                subclass(FunctionSemanticsInternal.DefaultConfigureBlockRequirement.DefaultNotAllowed::class)
                subclass(FunctionSemanticsInternal.DefaultConfigureBlockRequirement.DefaultRequired::class)
                subclass(FunctionSemanticsInternal.DefaultConfigureBlockRequirement.DefaultOptional::class)
            }
            polymorphic(ParameterSemantics::class) {
                subclass(ParameterSemanticsInternal.DefaultStoreValueInProperty::class)
                subclass(ParameterSemanticsInternal.DefaultUnknown::class)
            }
            polymorphic(SchemaFunction::class) {
                polymorphic(SchemaMemberFunction::class) {
                    subclass(DefaultDataBuilderFunction::class)
                    subclass(DefaultDataMemberFunction::class)
                }
                subclass(DefaultDataTopLevelFunction::class)
                subclass(DefaultDataConstructor::class)
            }
        }
        prettyPrint = true
        allowStructuredMapKeys = true
    }

    fun schemaToJsonString(analysisSchema: AnalysisSchema) = json.encodeToString(analysisSchema as DefaultAnalysisSchema)

    fun schemaFromJsonString(schemaString: String) = json.decodeFromString<DefaultAnalysisSchema>(schemaString)
}
