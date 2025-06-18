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

import org.gradle.internal.jvm.SupportedJavaVersions

class KnownProblemIds {

    static void assertIsKnown(ReceivedProblem problem) {
        assert problem != null
        def definition = problem.definition
        def knownDefinition = KNOWN_DEFINITIONS.find { it ->
            def pattern = it.key
            definition.id.fqid ==~ pattern
        }?.value
        assert knownDefinition != null: "Unknown problem id: ${definition.id.fqid}"
        assert knownDefinition instanceof List: "Known problem definition must be a list of expected display names"
        def definitionWithMatchingDisplayName = knownDefinition.find { definition.id.displayName ==~ it }
        assert definitionWithMatchingDisplayName != null, "Unexpected display name for problem: '${definition.id.displayName}'"

        def groupFqid = groupOf(definition.id.fqid)
        while (groupFqid != null) {
            def group = KNOWN_GROUPS[groupFqid]
            assert group != null: "Unknown problem group: ${groupFqid}"
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

    private static final Map<String, String> KNOWN_GROUPS = [
        'problems-api': 'Problems API',
        'verification': 'Verification',
        'configuration-usage': 'Configuration usage',
        'compilation': 'Compilation',
        'deprecation': 'Deprecation',
        'compilation:java': 'Java compilation',
        'plugin-application': 'Plugin application',
        'task-selection': 'Task selection',
        'dependency-version-catalog': 'Version catalog',
        'compilation:groovy-dsl': 'Groovy DSL script compilation',
        'verification:property-verification': 'Property verification problems',
        'verification:type-verification': 'Gradle type verification',
        'verification:configuration-cache': 'Configuration cache',

        // dependency resolution failures
        'dependency-variant-resolution': 'Dependency variant resolution',

        // groups from integration tests
        'generic': 'Generic'
    ]

    /**
     * This map is used to validate that problems reported have known IDs, and display name.
     * <p>
     * Both the key and value is handled as a regular expression if the value is too dynamic.
     */
    private static final HashMap<String, List<String>> KNOWN_DEFINITIONS = [
        'problems-api:missing-id': ['Problem id must be specified'],
        'problems-api:unsupported-additional-data': ['Unsupported additional data type'],
        'configuration-usage:name-not-allowed': ['Configuration name not allowed'],
        'compilation:groovy-dsl:compilation-failed': ['Groovy DSL script compilation problem'],
        // Flexible java compilation categories
        // The end of the category is matched with a regex, as there are many possible endings (and also changes with JDK versions)
        // See compiler.java for the full list of diagnostic codes we use as categories (we replace the dots with dashes)
        'compilation:java:compiler.*': ['.*'],
        'compilation:java:initialization-failed': ['Java compilation initialization error'],
        'dependency-version-catalog:alias-not-finished': ['Alias version not provided', 'Invalid alias'],
        'dependency-version-catalog:invalid-dependency-notation': ['Invalid dependency notation in TOML file'],
        'dependency-version-catalog:reserved-alias-name': ['Reserved alias name'],
        'dependency-version-catalog:catalog-file-does-not-exist': ['Problem: In version catalog libs, import of external catalog file failed.'],
        'dependency-version-catalog:toml-syntax-error': ['TOML syntax invalid.'],
        'dependency-version-catalog:too-many-import-files': ['Problem: In version catalog testLibs, importing multiple files are not supported.'],
        'dependency-version-catalog:too-many-import-invocation': ['Problem: In version catalog testLibs, you can only call the \'from\' method a single time.'],
        'dependency-version-catalog:no-import-files': ['Problem: In version catalog testLibs, no files are resolved to be imported.'],
        'deprecation:buildsrc-script': ['BuildSrc script has been deprecated.'],
        'deprecation:custom-task-action': ['Custom Task action has been deprecated.'],
        'deprecation:executing-gradle-on-jvm-versions-and-lower': ['Executing Gradle on JVM versions ' + (SupportedJavaVersions.FUTURE_MINIMUM_DAEMON_JAVA_VERSION - 1) +
                                                                       ' and lower has been deprecated.'],
        'deprecation:included-build-script': ['Included build script has been deprecated.'],
        'deprecation:included-build-task': ['Included build task has been deprecated.'],
        'deprecation:init-script': ['Init script has been deprecated.'],
        'deprecation:plugin': ['Plugin has been deprecated.'],
        'deprecation:plugin-script': ['Plugin script has been deprecated.'],
        'deprecation:the-detachedconfiguration-configuration-has-been-deprecated-for-consumption': ['The detachedConfiguration1 configuration has been deprecated for consumption.'],
        'deprecation:configurations-acting-as-both-root-and-variant': ['Configurations should not act as both a resolution root and a variant simultaneously.'],
        'deprecation:properties-should-be-assigned-using-the-propname-value-syntax-setting-a-property-via-the-gradle-generated-propname-value-or-propname-value-syntax-in-groovy-dsl': [
            'Properties should be assigned using the \'propName = value\' syntax. Setting a property via the Gradle-generated \'propName value\' or \'propName\\(value\\)\' syntax in Groovy DSL has been deprecated.'
        ],
        'deprecation:repository-jcenter': ['The RepositoryHandler.jcenter\\(\\) method has been deprecated.'],
        'task-selection:no-matches': ['No matches', 'cannot locate task'],
        'verification:configuration-cache:error-writing-value-of-type-org-gradle-api-internal-file-collections-defaultconfigurablefilecollection': [
            'error writing value of type \'org.gradle.api.internal.file.collections.DefaultConfigurableFileCollection\''],
        'verification:configuration-cache:registration-of-listener-on-gradle-buildfinished-is-unsupported': ['registration of listener on \'Gradle.buildFinished\' is unsupported'],
        'verification:configuration-cache:invocation-of-task-project-at-execution-time-is-unsupported-with-the-configuration-cache': [
            'invocation of \'Task.project\' at execution time is unsupported with the configuration cache.'],
        'plugin-application:target-type-mismatch': ['Unexpected plugin type'],
        'task-selection:ambiguous-matches': ['Ambiguous matches'],
        'task-selection:selection-failed': ['Selection failed'],
        'task-selection:empty-path': ['Empty path'],
        'missing-task-name': ['Missing task name'],
        'empty-segments': ['Empty segments'],
        'verification:property-verification:annotation-invalid-in-context': ['Invalid annotation in context'],
        'verification:property-verification:cannot-use-optional-on-primitive-types': ['Property should be annotated with @Optional'],
        'verification:property-verification:cannot-write-output': ['Property is not writable'],
        'verification:property-verification:cannot-write-to-reserved-location': ['Cannot write to reserved location'],
        'verification:property-verification:conflicting-annotations': ['Type has conflicting annotation'],
        'verification:property-verification:ignored-property-must-not-be-annotated': ['Has wrong combination of annotations'],
        'verification:property-verification:implicit-dependency': ['Property has implicit dependency'],
        'verification:property-verification:incompatible-annotations': ['Incompatible annotations'],
        'verification:property-verification:incorrect-use-of-input-annotation': ['Incorrect use of @Input annotation'],
        'verification:property-verification:input-file-does-not-exist': ['Input file does not exist'],
        'verification:property-verification:missing-annotation': ['Missing annotation'],
        'verification:property-verification:missing-normalization-annotation': ['Missing normalization'],
        'verification:property-verification:nested-map-unsupported-key-type': ['Unsupported nested map key'],
        'verification:property-verification:nested-type-unsupported': ['Nested type unsupported'],
        'verification:property-verification:mutable-type-with-setter': ['Mutable type with setter'],
        'verification:property-verification:private-getter-must-not-be-annotated': ['Private property with wrong annotation'],
        'verification:property-verification:unexpected-input-file-type': ['Unexpected input file type'],
        'verification:property-verification:unsupported-notation': ['Property has unsupported value'],
        'verification:property-verification:unknown-implementation': ['Unknown property implementation'],
        'verification:property-verification:unknown-implementation-nested': ['Unknown property implementation'],
        'verification:property-verification:unsupported-value-type': ['Unsupported value type'],
        'verification:property-verification:unsupported-value-type-for-input': ['Unsupported value type for @Input annotation'],
        'verification:property-verification:value-not-set': ['Value not set'],
        'verification:type-verification:ignored-annotations-on-method': ['Ignored annotations on method'],
        'verification:type-verification:invalid-use-of-type-annotation': ['Incorrect use of type annotation'],
        'verification:type-verification:not-cacheable-without-reason': ['Not cacheable without reason'],
        'verification:configuration-cache:cannot-serialize-object-of-type-org-gradle-api-defaulttask-a-subtype-of-org-gradle-api-task-as-these-are-not-supported-with-the-configuration-cache': [
            'cannot serialize object of type \'org.gradle.api.DefaultTask\', a subtype of \'org.gradle.api.Task\', as these are not supported with the configuration cache.'],
        'verification:missing-java-toolchain-plugin': ['Using task ValidatePlugins without applying the Java Toolchain plugin'],

        // dependency resolution failures
        'dependency-variant-resolution:configuration-not-compatible': ['Configuration selected by name is not compatible'],
        'dependency-variant-resolution:configuration-not-consumable': ['Configuration selected by name is not consumable'],
        'dependency-variant-resolution:configuration-does-not-exist': ['Configuration selected by name does not exist'],
        'dependency-variant-resolution:ambiguous-variants': ['Multiple variants exist that would match the request'],
        'dependency-variant-resolution:no-compatible-variants': ['No variants exist that would match the request'],
        'dependency-variant-resolution:no-variants-with-matching-capabilities': ['No variants exist with capabilities that would match the request'],

        'dependency-variant-resolution:ambiguous-artifact-transform': ['Multiple artifacts transforms exist that would satisfy the request'],
        'dependency-variant-resolution:no-compatible-artifact': ['No artifacts exist that would match the request'],
        'dependency-variant-resolution:ambiguous-artifacts': ['Multiple artifacts exist that would match the request'],
        'dependency-variant-resolution:unknown-artifact-selection-failure': ['Unknown artifact selection failure'],

        'dependency-variant-resolution:incompatible-multiple-nodes': ['Incompatible nodes of a single component were selected'],

        'dependency-variant-resolution:unknown-resolution-failure': ['Unknown resolution failure'],

        // integration test problems
        'deprecation:some-indirect-deprecation': ['Some indirect deprecation has been deprecated.'],
        'deprecation:some-invocation-feature': ['Some invocation feature has been deprecated.'],
        'deprecation:thing': ['Thing has been deprecated.'],
        'deprecation:typed-task': ['Typed task has been deprecated.'],
        'generic:deprecation:plugin': ['DisplayName'],
        'generic:type': ['label'],
        'generic:type0': ['This is the heading problem text0'],
        'generic:type1': ['This is the heading problem text1'],
        'generic:type2': ['This is the heading problem text2'],
        'generic:type3': ['This is the heading problem text3'],
        'generic:type4': ['This is the heading problem text4'],
        'generic:type5': ['This is the heading problem text5'],
        'generic:type6': ['This is the heading problem text6'],
        'generic:type7': ['This is the heading problem text7'],
        'generic:type8': ['This is the heading problem text8'],
        'generic:type9': ['This is the heading problem text9'],
        'generic:type11': ['inner'],
        'generic:type12': ['outer'],
    ]
}
