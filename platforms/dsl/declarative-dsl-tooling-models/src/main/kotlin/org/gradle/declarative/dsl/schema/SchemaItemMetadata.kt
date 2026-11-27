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

package org.gradle.declarative.dsl.schema

import org.gradle.tooling.ToolingModelContract
import java.io.Serializable

@ToolingModelContract(
    subTypes = [
        SchemaMemberOrigin::class,
        ContainerElementFactory::class,
        ProjectFeatureOrigin::class,
        ConfigureFromGetterOrigin::class,
        UnsafeSchemaItem::class,
        UnsafeNonInterfaceType::class,
        UnsafeNonAbstractMember::class,
        UnsafeInjectProperty::class,
        UnsafeJavaBeanProperty::class,
        UnsafeNonPureFunction::class,
        UnsafeBecauseHasHiddenMembers::class,
        UnsafeBecauseHasNonPublicMembers::class
    ]
)
sealed interface SchemaItemMetadata : Serializable

@ToolingModelContract(
    subTypes = [
        ContainerElementFactory::class,
        ProjectFeatureOrigin::class,
        ConfigureFromGetterOrigin::class
    ]
)
sealed interface SchemaMemberOrigin : SchemaItemMetadata

interface ContainerElementFactory : SchemaMemberOrigin {
    val elementType: DataTypeRef
}

interface ProjectFeatureOrigin : SchemaMemberOrigin {
    val featureName: String
    val featurePluginClassName: String
    val ecosystemPluginClassName: String
    val ecosystemPluginId: String?
    val targetDefinitionClassName: String?
    val targetBuildModelClassName: String?
    val isSafeDefinition: Boolean
    //TODO: feature owner plugin ID?
}

interface ConfigureFromGetterOrigin : SchemaMemberOrigin {
    val javaClassName: String
    val memberName: String
}

@ToolingModelContract(
    subTypes = [
        UnsafeNonInterfaceType::class,
        UnsafeNonAbstractMember::class,
        UnsafeInjectProperty::class,
        UnsafeJavaBeanProperty::class,
        UnsafeNonPureFunction::class,
        UnsafeBecauseHasHiddenMembers::class,
        UnsafeBecauseHasNonPublicMembers::class
    ]
)
interface UnsafeSchemaItem : SchemaItemMetadata

interface UnsafeNonInterfaceType : UnsafeSchemaItem
interface UnsafeNonAbstractMember : UnsafeSchemaItem
interface UnsafeInjectProperty : UnsafeSchemaItem
interface UnsafeJavaBeanProperty : UnsafeSchemaItem
interface UnsafeNonPureFunction : UnsafeSchemaItem
interface UnsafeBecauseHasHiddenMembers : UnsafeSchemaItem {
    val memberNames: List<String>
}
interface UnsafeBecauseHasNonPublicMembers : UnsafeSchemaItem {
    val memberNames: List<String>
}
