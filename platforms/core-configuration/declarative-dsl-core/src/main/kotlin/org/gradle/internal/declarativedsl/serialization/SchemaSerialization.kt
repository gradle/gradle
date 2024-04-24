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
import org.gradle.internal.declarativedsl.schemaimpl.AnalysisSchemaImpl
import org.gradle.internal.declarativedsl.schemaimpl.ConfigureAccessorImpl
import org.gradle.internal.declarativedsl.schemaimpl.DataBuilderFunctionImpl
import org.gradle.internal.declarativedsl.schemaimpl.DataClassImpl
import org.gradle.internal.declarativedsl.schemaimpl.DataConstructorImpl
import org.gradle.internal.declarativedsl.schemaimpl.DataMemberFunctionImpl
import org.gradle.internal.declarativedsl.schemaimpl.DataParameterImpl
import org.gradle.internal.declarativedsl.schemaimpl.DataPropertyImpl
import org.gradle.internal.declarativedsl.schemaimpl.DataTopLevelFunctionImpl
import org.gradle.internal.declarativedsl.schemaimpl.DataTypeImpl
import org.gradle.internal.declarativedsl.schemaimpl.DataTypeRefImpl
import org.gradle.internal.declarativedsl.schemaimpl.ExternalObjectProviderKeyImpl
import org.gradle.internal.declarativedsl.schemaimpl.FqNameImpl
import org.gradle.internal.declarativedsl.schemaimpl.FunctionSemanticsImpl
import org.gradle.internal.declarativedsl.schemaimpl.ParameterSemanticsImpl
import org.gradle.internal.declarativedsl.schema.AnalysisSchema
import org.gradle.internal.declarativedsl.schema.ConfigureAccessor
import org.gradle.internal.declarativedsl.schema.DataClass
import org.gradle.internal.declarativedsl.schema.DataParameter
import org.gradle.internal.declarativedsl.schema.DataProperty
import org.gradle.internal.declarativedsl.schema.DataType
import org.gradle.internal.declarativedsl.schema.DataTypeRef
import org.gradle.internal.declarativedsl.schema.ExternalObjectProviderKey
import org.gradle.internal.declarativedsl.schema.FqName
import org.gradle.internal.declarativedsl.schema.FunctionSemantics
import org.gradle.internal.declarativedsl.schema.ParameterSemantics
import org.gradle.internal.declarativedsl.schema.SchemaFunction
import org.gradle.internal.declarativedsl.schema.SchemaMemberFunction


object SchemaSerialization {

    private
    val json = Json {
        serializersModule = SerializersModule {
            polymorphic(AnalysisSchema::class) {
                subclass(AnalysisSchemaImpl::class)
            }
            polymorphic(SchemaFunction::class) {
                polymorphic(SchemaMemberFunction::class) {
                    subclass(DataBuilderFunctionImpl::class)
                    subclass(DataMemberFunctionImpl::class)
                }
                subclass(DataTopLevelFunctionImpl::class)
                subclass(DataConstructorImpl::class)
            }
            polymorphic(FunctionSemantics::class) {
                subclass(FunctionSemanticsImpl.PureImpl::class)
                subclass(FunctionSemanticsImpl.BuilderImpl::class)
                subclass(FunctionSemanticsImpl.AddAndConfigureImpl::class)
                subclass(FunctionSemanticsImpl.AccessAndConfigureImpl::class)
            }
            polymorphic(ConfigureAccessor::class) {
                subclass(ConfigureAccessorImpl.PropertyImpl::class)
                subclass(ConfigureAccessorImpl.CustomImpl::class)
                subclass(ConfigureAccessorImpl.ConfiguringLambdaArgumentImpl::class)
            }
            polymorphic(DataProperty.PropertyMode::class) {
                subclass(DataPropertyImpl.PropertyModeImpl.ReadWriteImpl::class)
                subclass(DataPropertyImpl.PropertyModeImpl.ReadOnlyImpl::class)
                subclass(DataPropertyImpl.PropertyModeImpl.WriteOnlyImpl::class)
            }
            polymorphic(DataType::class) {
                subclass(DataTypeImpl.IntDataTypeImpl::class)
                subclass(DataTypeImpl.LongDataTypeImpl::class)
                subclass(DataTypeImpl.StringDataTypeImpl::class)
                subclass(DataTypeImpl.BooleanDataTypeImpl::class)
                subclass(DataTypeImpl.NullTypeImpl::class)
                subclass(DataTypeImpl.UnitTypeImpl::class)
                polymorphic(DataClass::class) {
                    subclass(DataClassImpl::class)
                }
            }
            polymorphic(DataProperty::class) {
                subclass(DataPropertyImpl::class)
            }
            polymorphic(DataParameter::class) {
                subclass(DataParameterImpl::class)
            }
            polymorphic(ParameterSemantics::class) {
                subclass(ParameterSemanticsImpl.StoreValueInPropertyImpl::class)
                subclass(ParameterSemanticsImpl.UnknownImpl::class)
            }
            polymorphic(ExternalObjectProviderKey::class) {
                subclass(ExternalObjectProviderKeyImpl::class)
            }
            polymorphic(FunctionSemantics.ConfigureSemantics.ConfigureBlockRequirement::class) {
                subclass(FunctionSemanticsImpl.ConfigureBlockRequirementImpl.NotAllowedImpl::class)
                subclass(FunctionSemanticsImpl.ConfigureBlockRequirementImpl.RequiredImpl::class)
                subclass(FunctionSemanticsImpl.ConfigureBlockRequirementImpl.OptionalImpl::class)
            }
            polymorphic(FqName::class) {
                subclass(FqNameImpl::class)
            }
            polymorphic(FunctionSemantics.Builder::class) {
                subclass(FunctionSemanticsImpl.BuilderImpl::class)
            }
            polymorphic(DataTypeRef::class) {
                subclass(DataTypeRefImpl.NameImpl::class)
                subclass(DataTypeRefImpl.TypeImpl::class)
            }
            polymorphic(FunctionSemantics.AccessAndConfigure.ReturnType::class) {
                subclass(FunctionSemanticsImpl.AccessAndConfigureImpl.ReturnTypeImpl.UnitImpl::class)
                subclass(FunctionSemanticsImpl.AccessAndConfigureImpl.ReturnTypeImpl.ConfiguredObjectImpl::class)
            }
        }
        prettyPrint = true
        allowStructuredMapKeys = true
    }

    fun schemaToJsonString(analysisSchema: AnalysisSchema) = json.encodeToString(analysisSchema)

    fun schemaFromJsonString(schemaString: String) = json.decodeFromString<AnalysisSchema>(schemaString)
}
