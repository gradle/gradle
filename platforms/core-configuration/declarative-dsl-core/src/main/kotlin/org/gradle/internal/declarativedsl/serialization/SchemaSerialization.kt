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
import org.gradle.internal.declarativedsl.analysis.DefaultAnalysisSchema
import org.gradle.internal.declarativedsl.analysis.DataBuilderFunction
import org.gradle.internal.declarativedsl.analysis.DataMemberFunction
import org.gradle.declarative.dsl.schema.DataParameter
import org.gradle.internal.declarativedsl.analysis.DefaultDataParameter
import org.gradle.declarative.dsl.schema.DataProperty
import org.gradle.declarative.dsl.schema.DataType
import org.gradle.declarative.dsl.schema.DataTypeRef
import org.gradle.internal.declarativedsl.analysis.DefaultDataProperty
import org.gradle.declarative.dsl.schema.FqName
import org.gradle.declarative.dsl.schema.FunctionSemantics
import org.gradle.internal.declarativedsl.analysis.DefaultFqName
import org.gradle.declarative.dsl.schema.SchemaMemberFunction
import org.gradle.internal.declarativedsl.analysis.ConfigureAccessorInternal
import org.gradle.internal.declarativedsl.analysis.DataTypeRefInternal
import org.gradle.internal.declarativedsl.analysis.DefaultDataClass
import org.gradle.internal.declarativedsl.analysis.DefaultFunctionSemantics
import org.gradle.internal.declarativedsl.language.DataTypeInternal


object SchemaSerialization {

    private
    val json = Json {
        serializersModule = SerializersModule {
            polymorphic(ConfigureAccessor::class) {
                subclass(ConfigureAccessorInternal.ConfiguringLambdaArgument::class)
                subclass(ConfigureAccessorInternal.Custom::class)
                subclass(ConfigureAccessorInternal.Property::class)
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
                subclass(DefaultFunctionSemantics.DefaultAccessAndConfigure::class)
                subclass(DefaultFunctionSemantics.DefaultAddAndConfigure::class)
                subclass(DefaultFunctionSemantics.DefaultBuilder::class)
                subclass(DefaultFunctionSemantics.DefaultPure::class)
            }
            polymorphic(FunctionSemantics.AccessAndConfigure.ReturnType::class) {
                subclass(DefaultFunctionSemantics.DefaultAccessAndConfigure.DefaultReturnType.DefaultUnit::class)
                subclass(DefaultFunctionSemantics.DefaultAccessAndConfigure.DefaultReturnType.DefaultConfiguredObject::class)
            }
            polymorphic(FunctionSemantics.ConfigureSemantics.ConfigureBlockRequirement::class) {
                subclass(DefaultFunctionSemantics.DefaultConfigureBlockRequirement.DefaultNotAllowed::class)
                subclass(DefaultFunctionSemantics.DefaultConfigureBlockRequirement.DefaultRequired::class)
                subclass(DefaultFunctionSemantics.DefaultConfigureBlockRequirement.DefaultOptional::class)
            }
            polymorphic(SchemaMemberFunction::class) {
                subclass(DataMemberFunction::class)
                subclass(DataBuilderFunction::class)
            }
        }
        prettyPrint = true
        allowStructuredMapKeys = true
    }

    fun schemaToJsonString(analysisSchema: AnalysisSchema) = json.encodeToString(analysisSchema as DefaultAnalysisSchema)

    fun schemaFromJsonString(schemaString: String) = json.decodeFromString<DefaultAnalysisSchema>(schemaString)
}
