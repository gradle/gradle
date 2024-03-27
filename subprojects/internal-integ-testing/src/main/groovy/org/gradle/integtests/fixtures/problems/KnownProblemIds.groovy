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
        def definition = problem.definition
        def knownDefinition = KNOWN_DEFINITIONS[problem.definition.id.fqid]
        assert knownDefinition != null : "Unknown problem id: ${definition.id.fqid}"
        assert knownDefinition == definition.id.displayName : "Unexpected display name for problem: ${definition.id.fqid}. Expected=${knownDefinition}, actual=${definition.id.displayName}"

        def groupFqid = groupOf(definition.id.fqid)
        while (groupFqid != null) {
            def group = KNOWN_GROUPS[groupFqid]
            assert group != null : "Unknown problem group: ${groupFqid}"
            groupFqid = groupOf(groupFqid)
        }
    }

    private static def groupOf(String fqid) {
        int idx = fqid.lastIndexOf(':')
        if (idx > 0) {
            return fqid.substring(0, idx)
        } else {
            return null
        }
    }

    private static final def KNOWN_GROUPS = [
        'problems-api' : 'Problems API',
        'validation' : 'Validation',
        'compilation' : 'Compilation',
        'deprecation' : 'Deprecation',
        'compilation:java' : 'Java compilation',
        'task-selection' : 'Task selection',
        'dependency-version-catalog' : 'Version catalog',
        'compilation:groovy-dsl' : 'Groovy DSL script compilation',
        'validation:property-validation' : 'Property validation problems',
        'validation:type-validation' : 'Gradle type validation',

        // groups from integration tests
        'generic' : 'Generic'
    ]

    private static final def KNOWN_DEFINITIONS = [
        'problems-api:missing-id' : 'Problem id must be specified',
        'problems-api:invalid-additional-data' : 'ProblemBuilder.additionalData() only supports values of type String',
        'compilation:groovy-dsl:compilation-failed' : 'Groovy DSL script compilation problem',
        'compilation:java:java-compilation-error' : 'Java compilation error',
        'compilation:java:java-compilation-failed' : 'Java compilation error',
        'compilation:java:java-compilation-warning' : 'Java compilation warning',
        'compilation:java:java-compilation-advice' : 'Java compilation note',
        'dependency-version-catalog:alias-not-finished' : 'version catalog error',
        'dependency-version-catalog:invalid-dependency-notation' : 'Dependency version catalog problem',
        'dependency-version-catalog:reserved-alias-name' : 'version catalog error',
        'dependency-version-catalog:catalog-file-does-not-exist' : 'version catalog error',
        'dependency-version-catalog:toml-syntax-error' : 'Dependency version catalog problem',
        'dependency-version-catalog:too-many-import-files' : 'version catalog error',
        'dependency-version-catalog:too-many-import-invocation' : 'version catalog error',
        'dependency-version-catalog:no-import-files' : 'version catalog error',
        'deprecation:buildsrc-script-has-been-deprecated' : 'BuildSrc script has been deprecated.',
        'deprecation:creating-a-configuration-with-a-name-that-starts-with-detachedconfiguration-has-been-deprecated' : 'Creating a configuration with a name that starts with \'detachedConfiguration\' has been deprecated.',
        'deprecation:custom-task-action-has-been-deprecated' : 'Custom Task action has been deprecated.',
        'deprecation:using-task-validateplugins-without-applying-the-java-toolchain-plugin' : 'Using task ValidatePlugins without applying the Java Toolchain plugin.',
        'deprecation:included-build-script-has-been-deprecated' : 'Included build script has been deprecated.',
        'deprecation:included-build-task-has-been-deprecated' : 'Included build task has been deprecated.',
        'deprecation:init-script-has-been-deprecated' : 'Init script has been deprecated.',
        'deprecation:plugin-has-been-deprecated' : 'Plugin has been deprecated.',
        'deprecation:plugin-script-has-been-deprecated' : 'Plugin script has been deprecated.',
        'deprecation:some-indirect-deprecation-has-been-deprecated' : 'Some indirect deprecation has been deprecated.',
        'deprecation:some-invocation-feature-has-been-deprecated' : 'Some invocation feature has been deprecated.',
        'deprecation:the-detachedconfiguration-configuration-has-been-deprecated-for-consumption' : 'The detachedConfiguration1 configuration has been deprecated for consumption.',
        'deprecation:the-repositoryhandler-jcenter-method-has-been-deprecated' : 'The RepositoryHandler.jcenter() method has been deprecated.',
        'deprecation:thing-has-been-deprecated' : 'Thing has been deprecated.',
        'deprecation:typed-task-has-been-deprecated' : 'Typed task has been deprecated.',
        'deprecation:while-resolving-configuration-detachedconfiguration-it-was-also-selected-as-a-variant-configurations-should-not-act-as-both-a-resolution-root-and-a-variant-simultaneously-depending-on-the-resolved-configuration-in-this-manner-has-been-deprecated' : 'While resolving configuration \'detachedConfiguration1\', it was also selected as a variant. Configurations should not act as both a resolution root and a variant simultaneously. Depending on the resolved configuration in this manner has been deprecated.',
        'task-selection:no-matches' : 'cannot locate task',
        'validation:property-validation:annotation-invalid-in-context' : 'is annotated with invalid property type',
        'validation:property-validation:cannot-use-optional-on-primitive-types' : 'Property should be annotated with @Optional',
        'validation:property-validation:cannot-write-output' : 'property not writeable',
        'validation:property-validation:conflicting-annotations' : 'type has conflicting annotation',
        'validation:property-validation:ignored-property-must-not-be-annotated' : 'has wrong combination of annotations',
        'validation:property-validation:implicit-dependency' : 'Property has implicit dependency',
        'validation:property-validation:incompatible-annotations' : 'Wrong property annotation',
        'validation:property-validation:incorrect-use-of-input-annotation' : 'has @Input annotation used on property',
        'validation:property-validation:input-file-does-not-exist' : 'input not allowed for property',
        'validation:property-validation:missing-annotation' : 'property missing',
        'validation:property-validation:missing-normalization-annotation' : 'Missing normalization',
        'validation:property-validation:nested-map-unsupported-key-type' : 'where key of nested map',
        'validation:property-validation:nested-type-unsupported' : 'with nested type',
        'validation:property-validation:mutable-type-with-setter' : 'mutable type is writeable',
        'validation:property-validation:private-getter-must-not-be-annotated' : 'is private and with wrong annotation',
        'validation:property-validation:unexpected-input-file-type' : 'input not allowed for property',
        'validation:property-validation:unsupported-notation' : 'property has unsupported value',
        'validation:property-validation:unknown-implementation' : 'Problem with property',
        'validation:property-validation:unknown-implementation-nested' : 'Nested input problem for property',
        'validation:property-validation:unsupported-value-type' : 'property with unsupported annotation',
        'validation:property-validation:unsupported-value-type-for-input' : 'has @Input annotation used',
        'validation:property-validation:value-not-set' : 'doesn\'t have a configured value',
        'validation:type-validation:ignored-annotations-on-method' : 'method has wrong annotation',
        'validation:type-validation:invalid-use-of-type-annotation' : 'is incorrectly annotated',
        'validation:type-validation:not-cacheable-without-reason' : 'annotation missing',

        // integration test problems
        'generic:deprecation:plugin' : 'DisplayName',
        'generic:type' : 'label',
        'generic:type1' : 'inner',
        'generic:type2' : 'outer',
    ]
}
