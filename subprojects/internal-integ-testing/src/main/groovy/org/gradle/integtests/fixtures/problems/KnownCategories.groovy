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

class KnownCategories {

    static void assertHasKnownCategory(ReceivedProblem problem) {
        assert problem != null
        def category = problem['definition']['category']
        assert category != null : "Category must be present"
        assert category['namespace'] != null : "Must specify a namespace: $category"
        assert category['category'] != null : "Must specify a main category: $category"
        assert category['subcategories'] != null : "Must specify subcategories: $category"
        assert KNOWN_CATEGORIES.contains(category): "Unknown problem category: ${toGroovyMapNotation(category)}"
    }

    private static String toGroovyMapNotation(def category) {
        // this makes it easy to copy missing categories from the output of failed tests to this file
        def sc = (category['subcategories'] as List)
        "[namespace: \"${category['namespace']}\", category: \"${category['category']}\", subcategories: [${if(!sc.empty) {'"' + sc.join('", "') + '"'} else {''}}]]"
    }

    private static final def KNOWN_CATEGORIES = [
        [namespace: "org.gradle", category: "compilation", subcategories: ["groovy-dsl", "compilation-failed"]],
        [namespace: "org.gradle", category: "compilation", subcategories: ["java", "compilation-failed"]],
        [namespace: "org.gradle", category: "compilation", subcategories: ["java", "compilation-warning"]],
        [namespace: "org.gradle", category: "compilation", subcategories: ["java", "compilation-advice"]],
        [namespace: "org.gradle", category: "dependency-version-catalog", subcategories: ["alias-not-finished"]],
        [namespace: "org.gradle", category: "dependency-version-catalog", subcategories: ["reserved-alias-name"]],
        [namespace: "org.gradle", category: "dependency-version-catalog", subcategories: ["catalog-file-does-not-exist"]],
        [namespace: "org.gradle", category: "dependency-version-catalog", subcategories: ["toml-syntax-error"]],
        [namespace: "org.gradle", category: "dependency-version-catalog", subcategories: ["too-many-import-files"]],
        [namespace: "org.gradle", category: "dependency-version-catalog", subcategories: ["too-many-import-invocation"]],
        [namespace: "org.gradle", category: "dependency-version-catalog", subcategories: ["no-import-files"]],
        [namespace: "org.gradle", category: "deprecation", subcategories: ["build-invocation"]],
        [namespace: "org.gradle", category: "deprecation", subcategories: ["user-code-direct"]],
        [namespace: "org.gradle", category: "deprecation", subcategories: ["user-code-indirect"]],
        [namespace: "org.gradle", category: "task-selection", subcategories: ["no-matches"]],
        [namespace: "org.gradle", category: "type-validation", subcategories: ["property", "annotation-invalid-in-context"]],
        [namespace: "org.gradle", category: "type-validation", subcategories: ["property", "cannot-use-optional-on-primitive-types"]],
        [namespace: "org.gradle", category: "type-validation", subcategories: ["property", "cannot-write-output"]],
        [namespace: "org.gradle", category: "type-validation", subcategories: ["property", "conflicting-annotations"]],
        [namespace: "org.gradle", category: "type-validation", subcategories: ["property", "ignored-property-must-not-be-annotated"]],
        [namespace: "org.gradle", category: "type-validation", subcategories: ["property", "implicit-dependency"]],
        [namespace: "org.gradle", category: "type-validation", subcategories: ["property", "incompatible-annotations"]],
        [namespace: "org.gradle", category: "type-validation", subcategories: ["property", "incorrect-use-of-input-annotation"]],
        [namespace: "org.gradle", category: "type-validation", subcategories: ["property", "input-file-does-not-exist"]],
        [namespace: "org.gradle", category: "type-validation", subcategories: ["property", "missing-annotation"]],
        [namespace: "org.gradle", category: "type-validation", subcategories: ["property", "missing-normalization-annotation"]],
        [namespace: "org.gradle", category: "type-validation", subcategories: ["property", "nested-map-unsupported-key-type"]],
        [namespace: "org.gradle", category: "type-validation", subcategories: ["property", "nested-type-unsupported"]],
        [namespace: "org.gradle", category: "type-validation", subcategories: ["property", "mutable-type-with-setter"]],
        [namespace: "org.gradle", category: "type-validation", subcategories: ["property", "private-getter-must-not-be-annotated"]],
        [namespace: "org.gradle", category: "type-validation", subcategories: ["property", "unexpected-input-file-type"]],
        [namespace: "org.gradle", category: "type-validation", subcategories: ["property", "unsupported-notation"]],
        [namespace: "org.gradle", category: "type-validation", subcategories: ["property", "unknown-implementation"]],
        [namespace: "org.gradle", category: "type-validation", subcategories: ["property", "unsupported-value-type"]],
        [namespace: "org.gradle", category: "type-validation", subcategories: ["property", "value-not-set"]],
        [namespace: "org.gradle", category: "type-validation", subcategories: ["type", "ignored-annotations-on-method"]],
        [namespace: "org.gradle", category: "type-validation", subcategories: ["type", "invalid-use-of-type-annotation"]],
        [namespace: "org.gradle", category: "type-validation", subcategories: ["type", "not-cacheable-without-reason"]],

        // categories from integration tests
        [namespace: "org.gradle", category: "TEST_PROBLEM", subcategories: []],
        [namespace: "org.example.plugin", category: "deprecation", subcategories: ["plugin"]],
        [namespace: "org.example.plugin", category: "type", subcategories: []],
        [namespace: "org.example.plugin", category: "validation", subcategories: ["problems-api", "invalid-additional-data"]],
        [namespace: "org.example.plugin", category: "validation", subcategories: ["problems-api", "missing-category"]],
        [namespace: "org.example.plugin", category: "validation", subcategories: ["problems-api", "missing-label"]],
    ]
}
