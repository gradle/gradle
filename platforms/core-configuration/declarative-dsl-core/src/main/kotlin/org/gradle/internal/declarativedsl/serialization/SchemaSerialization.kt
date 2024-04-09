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
import org.gradle.internal.declarativedsl.analysis.AccessAndConfigureFunctionSemantics
import org.gradle.internal.declarativedsl.analysis.AddAndConfigureFunctionSemantics
import org.gradle.declarative.dsl.schema.AnalysisSchema
import org.gradle.internal.declarativedsl.analysis.AnalysisSchemaImpl
import org.gradle.internal.declarativedsl.analysis.BuilderFunctionSemantics
import org.gradle.internal.declarativedsl.analysis.DataBuilderFunction
import org.gradle.declarative.dsl.schema.DataClass
import org.gradle.internal.declarativedsl.analysis.DataClassImpl
import org.gradle.internal.declarativedsl.analysis.DataMemberFunction
import org.gradle.declarative.dsl.schema.DataParameter
import org.gradle.internal.declarativedsl.analysis.DataParameterImpl
import org.gradle.declarative.dsl.schema.DataProperty
import org.gradle.internal.declarativedsl.analysis.DataPropertyImpl
import org.gradle.internal.declarativedsl.analysis.DataTypeRefNameImpl
import org.gradle.internal.declarativedsl.analysis.DataTypeRefTypeImpl
import org.gradle.declarative.dsl.schema.DataTypeRef
import org.gradle.declarative.dsl.schema.DataType
import org.gradle.declarative.dsl.schema.FqName
import org.gradle.internal.declarativedsl.analysis.FqNameImpl
import org.gradle.declarative.dsl.schema.FunctionSemantics
import org.gradle.declarative.dsl.schema.ParameterSemantics
import org.gradle.internal.declarativedsl.analysis.PureFunctionSemantics
import org.gradle.declarative.dsl.schema.SchemaMemberFunction
import org.gradle.internal.declarativedsl.analysis.StoreValueInPropertyParameterSemantics
import org.gradle.internal.declarativedsl.analysis.UnknownParameterSemantics
import org.gradle.internal.declarativedsl.language.BooleanDataType
import org.gradle.internal.declarativedsl.language.IntDataType
import org.gradle.internal.declarativedsl.language.LongDataType
import org.gradle.internal.declarativedsl.language.NullDataType
import org.gradle.internal.declarativedsl.language.StringDataType
import org.gradle.internal.declarativedsl.language.UnitDataType


object SchemaSerialization {

    private
    val json = Json {
        serializersModule = SerializersModule {
            polymorphic(DataType::class) {
                subclass(IntDataType::class)
                subclass(LongDataType::class)
                subclass(StringDataType::class)
                subclass(BooleanDataType::class)
                subclass(NullDataType::class)
                subclass(UnitDataType::class)
            }
            polymorphic(DataTypeRef::class) {
                subclass(DataTypeRefNameImpl::class)
                subclass(DataTypeRefTypeImpl::class)
            }
            polymorphic(DataClass::class) {
                subclass(DataClassImpl::class)
            }
            polymorphic(DataParameter::class) {
                subclass(DataParameterImpl::class)
            }
            polymorphic(DataProperty::class) {
                subclass(DataPropertyImpl::class)
            }
            polymorphic(FqName::class) {
                subclass(FqNameImpl::class)
            }
            polymorphic(FunctionSemantics::class) {
                subclass(AccessAndConfigureFunctionSemantics::class)
                subclass(AddAndConfigureFunctionSemantics::class)
                subclass(PureFunctionSemantics::class)
                subclass(BuilderFunctionSemantics::class)
            }
            polymorphic(ParameterSemantics::class) {
                subclass(StoreValueInPropertyParameterSemantics::class)
                subclass(UnknownParameterSemantics::class)
            }
            polymorphic(SchemaMemberFunction::class) {
                subclass(DataMemberFunction::class)
                subclass(DataBuilderFunction::class)
            }
        }
        prettyPrint = true
        allowStructuredMapKeys = true
    }

    fun schemaToJsonString(analysisSchema: AnalysisSchema) = json.encodeToString(analysisSchema as AnalysisSchemaImpl)

    fun schemaFromJsonString(schemaString: String) = json.decodeFromString<AnalysisSchemaImpl>(schemaString)
}
