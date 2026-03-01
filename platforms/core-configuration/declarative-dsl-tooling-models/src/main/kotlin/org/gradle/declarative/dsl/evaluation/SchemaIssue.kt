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

package org.gradle.declarative.dsl.evaluation

import org.gradle.tooling.ToolingModelContract
import java.io.Serializable

@ToolingModelContract(subTypes = [
    SchemaIssue.DeclarationBothHiddenAndVisible::class,
    SchemaIssue.IllegalUsageOfTypeParameterBoundByClass::class,
    SchemaIssue.IllegalVarianceInParameterizedTypeUsage::class,
    SchemaIssue.HiddenTypeUsedInDeclaration::class,
    SchemaIssue.UnsupportedPairFactory::class,
    SchemaIssue.UnsupportedMapFactory::class,
    SchemaIssue.UnsupportedNullableType::class,
    SchemaIssue.UnsupportedVarargType::class,
    SchemaIssue.UnsupportedGenericContainerType::class,
    SchemaIssue.UnsupportedTypeParameterAsContainerType::class,
    SchemaIssue.UnsupportedNullableReadOnlyProperty::class,
    SchemaIssue.NonClassifiableType::class,
    SchemaIssue.UnitAddingFunctionWithLambda::class,
    SchemaIssue.UnrecognizedMember::class
])
interface SchemaIssue : Serializable {
    interface DeclarationBothHiddenAndVisible : SchemaIssue

    interface IllegalUsageOfTypeParameterBoundByClass : SchemaIssue{
        val typeParameterName: String
    }

    interface IllegalVarianceInParameterizedTypeUsage : SchemaIssue {
        val varianceKind: String
    }

    interface HiddenTypeUsedInDeclaration : SchemaIssue {
        val hiddenClassName: String
        val hiddenBecause: Iterable<String> // TODO: structured entries?
        val illegalUsages: Iterable<String> // TODO: structured entries?
    }

    interface UnsupportedPairFactory : SchemaIssue {
        val returnTypeName: String
    }

    interface UnsupportedMapFactory : SchemaIssue {
        val returnTypeName: String
    }

    interface UnsupportedNullableType : SchemaIssue {
        val typeName: String
    }

    interface UnsupportedVarargType : SchemaIssue {
        val typeName: String
    }

    interface UnsupportedGenericContainerType : SchemaIssue {
        val typeName: String
    }

    interface UnsupportedTypeParameterAsContainerType : SchemaIssue {
        val typeName: String
    }

    interface UnsupportedNullableReadOnlyProperty : SchemaIssue

    interface NonClassifiableType : SchemaIssue {
        val typeName: String
    }

    interface UnitAddingFunctionWithLambda : SchemaIssue

    interface UnrecognizedMember : SchemaIssue
}
