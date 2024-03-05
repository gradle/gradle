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

package org.gradle.integtests.fixtures.problems

class KnownProblemIds {

    static void assertHasKnownId(ReceivedProblem problem) {
        assert problem != null
        def id = problem['definition']['id']
        assert id != null : "Id must be present"
        assert id['name'] != null : "Must specify a main id: $id"
        assert id['displayName'] != null : "Must specify displayName: $id"
        def fqId = toFullyQualifiedId(id)
        assert KNOWN_IDS.contains(fqId): "Unknown problem id: ${fqId}"
    }

    private static String toFullyQualifiedId(problem) {
         return "${problem['parent'] ? toFullyQualifiedId(problem['parent']) + ":" :  ""}" + problem['name']
    }

    private static final def KNOWN_IDS = [
        'problems-api:missing-id',
        'compilation:groovy-dsl:compilation-failed',
        'compilation:java:java-compilation-error',
        'compilation:java:java-compilation-failed',
        'compilation:java:java-compilation-warning',
        'compilation:java:java-compilation-advice',
        'dependency-version-catalog:alias-not-finished',
        'dependency-version-catalog:reserved-alias-name',
        'dependency-version-catalog:catalog-file-does-not-exist',
        'dependency-version-catalog:toml-syntax-error',
        'dependency-version-catalog:too-many-import-files',
        'dependency-version-catalog:too-many-import-invocation',
        'dependency-version-catalog:no-import-files',
        'deprecation:deprecated-feature-used',
        'task-selection:no-matches',
        'validation:property-validation:annotation-invalid-in-context',
        'validation:property-validation:cannot-use-optional-on-primitive-types',
        'validation:property-validation:cannot-write-output',
        'validation:property-validation:conflicting-annotations',
        'validation:property-validation:ignored-property-must-not-be-annotated',
        'validation:property-validation:implicit-dependency',
        'validation:property-validation:incompatible-annotations',
        'validation:property-validation:incorrect-use-of-input-annotation',
        'validation:property-validation:input-file-does-not-exist',
        'validation:property-validation:missing-annotation',
        'validation:property-validation:missing-normalization-annotation',
        'validation:property-validation:nested-map-unsupported-key-type',
        'validation:property-validation:nested-type-unsupported',
        'validation:property-validation:mutable-type-with-setter',
        'validation:property-validation:private-getter-must-not-be-annotated',
        'validation:property-validation:unexpected-input-file-type',
        'validation:property-validation:unsupported-notation',
        'validation:property-validation:unknown-implementation',
        'validation:property-validation:unsupported-value-type',
        'validation:property-validation:value-not-set',
        'validation:type-validation:ignored-annotations-on-method',
        'validation:type-validation:invalid-use-of-type-annotation',
        'validation:type-validation:not-cacheable-without-reason',

        // categories from integration tests
        'TEST_PROBLEM',
        'generic:deprecation:plugin',
        'generic:type',
        'problems-api:invalid-additional-data',
        'problems-api:missing-category',
        'problems-api:missing-label',
    ]
}
