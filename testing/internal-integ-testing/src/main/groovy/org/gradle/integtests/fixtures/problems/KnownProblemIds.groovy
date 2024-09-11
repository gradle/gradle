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

    static void assertIsKnown(ReceivedProblem problem) {
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
        'plugin-application' : 'Plugin application',
        'task-selection' : 'Task selection',
        'dependency-version-catalog' : 'Version catalog',
        'compilation:groovy-dsl' : 'Groovy DSL script compilation',
        'validation:property-validation' : 'Property validation problems',
        'validation:type-validation' : 'Gradle type validation',
        'validation:configuration-cache' : 'Configuration cache',

        // dependency resolution failures
        'dependency-variant-resolution' : 'Dependency variant resolution',

        // groups from integration tests
        'generic' : 'Generic'
    ]

    private static final def KNOWN_DEFINITIONS = [
        'problems-api:missing-id' : 'Problem id must be specified',
        'problems-api:unsupported-additional-data' : 'Unsupported additional data type',
        'compilation:groovy-dsl:compilation-failed' : 'Groovy DSL script compilation problem',
        'compilation:java:initialization-failed' : 'Java compilation initialization error',
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
        'deprecation:buildsrc-script' : 'BuildSrc script has been deprecated.',
        'deprecation:creating-a-configuration-with-a-name-that-starts-with-detachedconfiguration' : 'Creating a configuration with a name that starts with \'detachedConfiguration\' has been deprecated.',
        'deprecation:custom-task-action' : 'Custom Task action has been deprecated.',
        'deprecation:executing-gradle-on-jvm-versions-and-lower': 'Executing Gradle on JVM versions 16 and lower has been deprecated.',
        'deprecation:missing-java-toolchain-plugin' : 'Using task ValidatePlugins without applying the Java Toolchain plugin.',
        'deprecation:included-build-script' : 'Included build script has been deprecated.',
        'deprecation:included-build-task' : 'Included build task has been deprecated.',
        'deprecation:init-script' : 'Init script has been deprecated.',
        'deprecation:plugin' : 'Plugin has been deprecated.',
        'deprecation:plugin-script' : 'Plugin script has been deprecated.',
        'deprecation:the-detachedconfiguration-configuration-has-been-deprecated-for-consumption' : 'The detachedConfiguration1 configuration has been deprecated for consumption.',
        'deprecation:configurations-acting-as-both-root-and-variant' : 'Configurations should not act as both a resolution root and a variant simultaneously.',
        'deprecation:repository-jcenter' : 'The RepositoryHandler.jcenter() method has been deprecated.',
        'task-selection:no-matches' : 'cannot locate task',
        'validation:configuration-cache:registration-of-listener-on-gradle-buildfinished-is-unsupported' : 'registration of listener on \'Gradle.buildFinished\' is unsupported',
        'validation:configuration-cache:invocation-of-task-project-at-execution-time-is-unsupported' : 'invocation of \'Task.project\' at execution time is unsupported.',
        'plugin-application:target-type-mismatch' : 'Unexpected plugin type',
        'task-selection:ambiguous-matches' : 'Ambiguous matches',
        'task-selection:no-matches' : 'No matches',
        'task-selection:selection-failed' : 'Selection failed',
        'task-selection:empty-path' : 'Empty path',
        'missing-task-name' : 'Missing task name',
        'empty-segments' : 'Empty segments',
        'validation:property-validation:annotation-invalid-in-context' : 'Invalid annotation in context',
        'validation:property-validation:cannot-use-optional-on-primitive-types' : 'Property should be annotated with @Optional',
        'validation:property-validation:cannot-write-output' : 'Property is not writable',
        'validation:property-validation:cannot-write-to-reserved-location' : 'Cannot write to reserved location',
        'validation:property-validation:conflicting-annotations' : 'Type has conflicting annotation',
        'validation:property-validation:ignored-property-must-not-be-annotated' : 'Has wrong combination of annotations',
        'validation:property-validation:implicit-dependency' : 'Property has implicit dependency',
        'validation:property-validation:incompatible-annotations' : 'Incompatible annotations',
        'validation:property-validation:incorrect-use-of-input-annotation' : 'Incorrect use of @Input annotation',
        'validation:property-validation:input-file-does-not-exist' : 'Input file does not exist',
        'validation:property-validation:missing-annotation' : 'Missing annotation',
        'validation:property-validation:missing-normalization-annotation' : 'Missing normalization',
        'validation:property-validation:nested-map-unsupported-key-type' : 'Unsupported nested map key',
        'validation:property-validation:nested-type-unsupported' : 'Nested type unsupported',
        'validation:property-validation:mutable-type-with-setter' : 'Mutable type with setter',
        'validation:property-validation:private-getter-must-not-be-annotated' : 'Private property with wrong annotation',
        'validation:property-validation:unexpected-input-file-type' : 'Unexpected input file type',
        'validation:property-validation:unsupported-notation' : 'Property has unsupported value',
        'validation:property-validation:unknown-implementation' : 'Unknown property implementation',
        'validation:property-validation:unknown-implementation-nested' : 'Unknown property implementation',
        'validation:property-validation:unsupported-value-type' : 'Unsupported value type',
        'validation:property-validation:unsupported-value-type-for-input' : 'Unsupported value type for @Input annotation',
        'validation:property-validation:value-not-set' : 'Value not set',
        'validation:type-validation:ignored-annotations-on-method' : 'Ignored annotations on method',
        'validation:type-validation:invalid-use-of-type-annotation' : 'Incorrect use of type annotation',
        'validation:type-validation:not-cacheable-without-reason' : 'Not cacheable without reason',
        'validation:configuration-cache:cannot-serialize-object-of-type-org-gradle-api-defaulttask-a-subtype-of-org-gradle-api-task-as-these-are-not-supported-with-the-configuration-cache' : 'cannot serialize object of type \'org.gradle.api.DefaultTask\', a subtype of \'org.gradle.api.Task\', as these are not supported with the configuration cache.',

        // dependency resolution failures
        'dependency-variant-resolution:configuration-not-compatible' : 'Configuration selected by name is not compatible',
        'dependency-variant-resolution:configuration-not-consumable' : 'Configuration selected by name is not consumable',
        'dependency-variant-resolution:configuration-does-not-exist' : 'Configuration selected by name does not exist',
        'dependency-variant-resolution:ambiguous-variants' : 'Multiple variants exist that would match the request',
        'dependency-variant-resolution:no-compatible-variants' : 'No variants exist that would match the request',
        'dependency-variant-resolution:no-variants-with-matching-capabilities' : 'No variants exist with capabilities that would match the request',

        'dependency-variant-resolution:ambiguous-artifact-transform' : 'Multiple artifacts transforms exist that would satisfy the request',
        'dependency-variant-resolution:no-compatible-artifact' : 'No artifacts exist that would match the request',
        'dependency-variant-resolution:ambiguous-artifacts' : 'Multiple artifacts exist that would match the request',
        'dependency-variant-resolution:unknown-artifact-selection-failure' : 'Unknown artifact selection failure',

        'dependency-variant-resolution:incompatible-multiple-nodes' : 'Incompatible nodes of a single component were selected',

        'dependency-variant-resolution:unknown-resolution-failure' : 'Unknown resolution failure',

        // integration test problems
        'deprecation:some-indirect-deprecation' : 'Some indirect deprecation has been deprecated.',
        'deprecation:some-invocation-feature' : 'Some invocation feature has been deprecated.',
        'deprecation:thing' : 'Thing has been deprecated.',
        'deprecation:typed-task' : 'Typed task has been deprecated.',
        'generic:deprecation:plugin' : 'DisplayName',
        'generic:type' : 'label',
        'generic:type0': 'This is the heading problem text0',
        'generic:type1': 'This is the heading problem text1',
        'generic:type2': 'This is the heading problem text2',
        'generic:type3': 'This is the heading problem text3',
        'generic:type4': 'This is the heading problem text4',
        'generic:type5': 'This is the heading problem text5',
        'generic:type6': 'This is the heading problem text6',
        'generic:type7': 'This is the heading problem text7',
        'generic:type8': 'This is the heading problem text8',
        'generic:type9': 'This is the heading problem text9',
        'generic:type11' : 'inner',
        'generic:type12' : 'outer',
    ]
}
