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

import org.gradle.api.internal.catalog.problems.VersionCatalogProblemId
import org.gradle.api.problems.ProblemGroup
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
        assert definitionWithMatchingDisplayName != null, "Unexpected display name for problem: '${definition.id.displayName}"

        // Walk the group objects rather than splitting the fqid: problem names may contain ':'
        for (def group = definition.id.group; group != null; group = group.parent) {
            def groupFqid = fqidOf(group)
            assert KNOWN_GROUPS[groupFqid] != null: "Unknown problem group: ${groupFqid}"
        }
    }

    private static String fqidOf(ProblemGroup group) {
        group.parent == null ? group.name : fqidOf(group.parent) + ':' + group.name
    }

    private static final Map<String, String> KNOWN_GROUPS = [
        // Top level
        'validation': 'Validation',
        'configuration-usage': 'Configuration usage',
        'daemon-toolchain' : 'Daemon toolchain',
        'jvm-toolchain': 'JVM Toolchain',
        'packaging': 'Packaging',
        'plugin-application': 'Plugin application',

        // Sub-groups
        'packaging:signing': 'Signing',
        'daemon-toolchain:configuration-generation' : 'Gradle configuration generation',
        'validation:configuration-cache': 'Configuration cache',

        // predefined groups (org.gradle.api.problems.ProblemGroups) used from integration tests
        'Gradle': 'Gradle',
        'Gradle:Build Definition': 'Build Definition',
        'Gradle:Build Logic': 'Build Logic',
        'Gradle:Deprecation': 'Deprecation',
        'Gradle:Invocation': 'Invocation',
        'Gradle:DSL Evaluation': 'DSL Evaluation',
        'Gradle:Plugin Validation': 'Plugin Validation',
        'Compilation': 'Compilation',
        'Dependencies': 'Dependencies',
        'Dependencies:Declaration': 'Declaration',
        'Dependencies:Graph Resolution': 'Graph Resolution',
        'Dependencies:Artifact Resolution': 'Artifact Resolution',
        'Compilation:Groovy': 'Groovy',
        'Compilation:Java': 'Java',
        'Compilation:Java:Undefined': 'Undefined',
        'Compilation:java': 'java',
        'Compilation:Kotlin': 'Kotlin',
        'Compilation:Undefined': 'Undefined',
        'Transformation': 'Transformation',
        'Transformation:KMP': 'KMP',
        'Transformation:KMP:JavaScript': 'JavaScript',
        'Others': 'Others',
        'Others:Undefined': 'Undefined',

        // groups from integration tests
        'generic': 'Generic',
        'issues': 'issues',
        'sample-problems': 'Sample Problems',
        'scripts': 'Scripts',
        'root': 'root',
    ]

    /**
     * This map is used to validate that problems reported have known IDs, and display name.
     * <p>
     * Both the key and value is handled as a regular expression if the value is too dynamic.
     */
    private static final HashMap<String, List<String>> KNOWN_DEFINITIONS = [
        // predefined groups (org.gradle.api.problems.ProblemGroups) used from integration tests
        'Compilation:Java:Unused import': ['Unused import'],
        'Compilation:java:Unused import': ['Unused import'],
        'Compilation:Kotlin:Unused import': ['Unused import'],
        'Compilation:Undefined:Unknown compiler': ['Unknown compiler'],
        'Compilation:Java:Undefined:Unknown compiler': ['Unknown compiler'],
        'Gradle:Build Definition:Deprecated plugin applied': ['Deprecated plugin applied'],
        'Gradle:DSL Evaluation:Script compilation failed': ['Script compilation failed'],
        'Transformation:KMP:JavaScript:Bundle failed': ['Bundle failed'],
        'Transformation:KMP:Compilation failed': ['Compilation failed'],
        'Others:Undefined:Something odd': ['Something odd'],
        'Gradle:Build Logic:Reported problem has no id': ['Reported problem has no id'],
        'Gradle:Build Logic:Reported problem id has no group': ['Reported problem id has no group'],
        'Gradle:Build Logic:Unsupported additional data type in reported problem': ['Unsupported additional data type in reported problem'],
        'configuration-usage:name-not-allowed': ['Configuration name not allowed'],
        'Compilation:Java:Compiler initialization failed': ['Compiler initialization failed'],
        'Compilation:Groovy:tools.jar is missing': ['tools.jar is missing'],
        // Java compiler diagnostics are named after javac's message templates, an open set that changes with the JDK
        // version; see JavacDiagnosticNames in java-compiler-worker.
        'Compilation:Java:.+' : ['.*'],
        'daemon-toolchain:configuration-generation:task-configuration' : ['Invalid task configuration'],
        'Dependencies:Declaration:Version catalog accessor name clash': [VersionCatalogProblemId.ACCESSOR_NAME_CLASH.name],
        'jvm-toolchain:invalid-jvm-installation': ['Invalid JVM installation'],
        'Dependencies:Declaration:Version catalog alias builder not finished': [VersionCatalogProblemId.ALIAS_NOT_FINISHED.name],
        'Dependencies:Declaration:Invalid version catalog dependency notation': [VersionCatalogProblemId.INVALID_DEPENDENCY_NOTATION.name],
        'Dependencies:Declaration:Invalid version catalog TOML definition': [VersionCatalogProblemId.INVALID_TOML_DEFINITION.name],
        'Dependencies:Declaration:Invalid version notation in version catalog': [VersionCatalogProblemId.INVALID_VERSION_NOTATION.name],
        'Dependencies:Declaration:Invalid version notation': ['Invalid version notation'],
        'Dependencies:Declaration:Reserved version catalog alias name': [VersionCatalogProblemId.RESERVED_ALIAS_NAME.name],
        'Dependencies:Declaration:Import of external version catalog file failed': [VersionCatalogProblemId.CATALOG_FILE_DOES_NOT_EXIST.name],
        'Dependencies:Declaration:Version catalog TOML syntax error': [VersionCatalogProblemId.TOML_SYNTAX_ERROR.name],
        'Dependencies:Declaration:Importing multiple version catalog files is not supported': [VersionCatalogProblemId.TOO_MANY_IMPORT_FILES.name],
        "Dependencies:Declaration:Multiple 'from' invocations in version catalog": [VersionCatalogProblemId.TOO_MANY_IMPORT_INVOCATION.name],
        'Dependencies:Declaration:No version catalog files were resolved to be imported': [VersionCatalogProblemId.NO_IMPORT_FILES.name],
        'Gradle:Deprecation:Implicit dependency between tasks in different builds': ['Implicit dependency between tasks in different builds'],
        'Gradle:Deprecation:Implicit lookup of methods in parent projects': ['Implicit lookup of methods in parent projects'],
        'Gradle:Deprecation:Implicit lookup of properties in parent projects': ['Implicit lookup of properties in parent projects'],
        'Gradle:Deprecation:BuildSrc script': ['BuildSrc script'],
        'Gradle:Deprecation:Custom Task action': ['Custom Task action'],
        'Gradle:Deprecation:Task usage': ['Task usage'],
        'Gradle:Deprecation:Executing Gradle on JVM versions \\d+ and lower': ['Executing Gradle on JVM versions ' + (SupportedJavaVersions.FUTURE_MINIMUM_DAEMON_JAVA_VERSION - 1) + ' and lower'],
        'Gradle:Deprecation:Included build script': ['Included build script'],
        'Gradle:Deprecation:Included build task': ['Included build task'],
        'Gradle:Deprecation:Init script': ['Init script'],
        'Gradle:Deprecation:Plugin': ['Plugin'],
        'Gradle:Deprecation:Plugin script': ['Plugin script'],
        'Gradle:Deprecation:Configuration usage': ['Configuration usage'],
        'Gradle:Deprecation:Configurations should not act as both a resolution root and a variant simultaneously.': ['Configurations should not act as both a resolution root and a variant simultaneously.'],
        'Gradle:Deprecation:Property set via the Gradle-generated \'propName value\' or \'propName\\(value\\)\' syntax in Groovy DSL': ['Property set via the Gradle-generated \'propName value\' or \'propName\\(value\\)\' syntax in Groovy DSL'],
        'Gradle:Deprecation:Querying the output of an artifact transform from a task action without declaring it as a task input': ['Querying the output of an artifact transform from a task action without declaring it as a task input'],
        'Gradle:Deprecation:Method usage': ['Method usage'],
        'root:test-problem': ['test problem'],
        'Gradle:Invocation:Task not found': ['Task not found'],
        'Gradle:Invocation:Project not found': ['Project not found'],
        'validation:configuration-cache:error-writing-value-of-type-org-gradle-api-internal-file-collections-defaultconfigurablefilecollection': ['error writing value of type \'org.gradle.api.internal.file.collections.DefaultConfigurableFileCollection\''],
        'validation:configuration-cache:registration-of-listener-on-gradle-buildfinished-is-unsupported': ['registration of listener on \'Gradle.buildFinished\' is unsupported'],
        'validation:configuration-cache:invocation-of-task-project-at-execution-time-is-unsupported-with-the-configuration-cache': ['invocation of \'Task.project\' at execution time is unsupported with the configuration cache.'],
        'validation:configuration-cache:isolated-projects-dangerously-ignoring-problems': ['Isolated Projects problems are dangerously ignored'],
        'packaging:signing:no-configured-signatory': ['No configured signatory'],
        'plugin-application:target-type-mismatch': ['Plugin applied to the wrong target'],
        'Gradle:Invocation:Ambiguous task name': ['Ambiguous task name'],
        'Gradle:Invocation:Ambiguous project name': ['Ambiguous project name'],
        'Gradle:Invocation:Task selection failed': ['Task selection failed'],
        'Gradle:Invocation:Project selection failed': ['Project selection failed'],
        'Gradle:Invocation:Empty task path': ['Empty task path'],
        'Gradle:Invocation:Missing task name': ['Missing task name'],
        'Gradle:Invocation:Task path with empty segments': ['Task path with empty segments'],
        'Gradle:Invocation:Exclusive task run with other tasks': ['Exclusive task run with other tasks'],
        'validation:configuration-cache:cannot-serialize-object-of-type-org-gradle-api-defaulttask-a-subtype-of-org-gradle-api-task-as-these-are-not-supported-with-the-configuration-cache': ['cannot serialize object of type \'org.gradle.api.DefaultTask\', a subtype of \'org.gradle.api.Task\', as these are not supported with the configuration cache.'],
        // Dynamic fqid until the CC class-encoding failure path emits a stable problem ID.
        'validation:configuration-cache:class-.*-cannot-be-encoded-because.*': ['(?s)Class .* cannot be encoded because.*'],
        'validation:configuration-cache:configuration-cache-warn-mode': ['Configuration Cache warn mode is enabled'],

        // Plugin Validation
        'Gradle:Plugin Validation:@ServiceReference property is not a build service': ['@ServiceReference property is not a build service'],
        'Gradle:Plugin Validation:Artifact transform should not declare output': ['Artifact transform should not declare output'],
        'Gradle:Plugin Validation:Conflicting annotations': ['Conflicting annotations'],
        'Gradle:Plugin Validation:Ignored annotations on method': ['Ignored annotations on method'],
        'Gradle:Plugin Validation:Ignored annotations on property': ['Ignored annotations on property'],
        'Gradle:Plugin Validation:Ignored property has other annotations': ['Ignored property has other annotations'],
        'Gradle:Plugin Validation:Incompatible annotations': ['Incompatible annotations'],
        'Gradle:Plugin Validation:Incorrect annotations on field': ['Incorrect annotations on field'],
        'Gradle:Plugin Validation:Incorrect use of @Input annotation': ['Incorrect use of @Input annotation'],
        'Gradle:Plugin Validation:Incorrect use of type annotation': ['Incorrect use of type annotation'],
        'Gradle:Plugin Validation:Invalid annotation in context': ['Invalid annotation in context'],
        'Gradle:Plugin Validation:Missing annotation': ['Missing annotation'],
        'Gradle:Plugin Validation:Missing normalization': ['Missing normalization'],
        'Gradle:Plugin Validation:Mutable type with setter': ['Mutable type with setter'],
        'Gradle:Plugin Validation:Nested type unsupported': ['Nested type unsupported'],
        'Gradle:Plugin Validation:Not cacheable without reason': ['Not cacheable without reason'],
        'Gradle:Plugin Validation:Primitive property annotated with @Optional': ['Primitive property annotated with @Optional'],
        'Gradle:Plugin Validation:Private method with wrong annotation': ['Private method with wrong annotation'],
        'Gradle:Plugin Validation:Private property with wrong annotation': ['Private property with wrong annotation'],
        'Gradle:Plugin Validation:Property declared to be sensitive to absolute paths': ['Property declared to be sensitive to absolute paths'],
        'Gradle:Plugin Validation:Property has redundant getters': ['Property has redundant getters'],
        'Gradle:Plugin Validation:Unsupported nested map key': ['Unsupported nested map key'],
        'Gradle:Plugin Validation:Unsupported value type': ['Unsupported value type'],
        'Gradle:Plugin Validation:Unsupported value type for @Input annotation': ['Unsupported value type for @Input annotation'],

        // Build Logic
        'Gradle:Build Logic:Missing project feature annotation': ['Missing project feature annotation'],
        'Gradle:Build Logic:Property cannot carry dependency': ['Property cannot carry dependency'],
        'Gradle:Build Logic:Property has implicit dependency': ['Property has implicit dependency'],
        'Gradle:Build Logic:Unknown implementation': ['Unknown implementation'],
        'Gradle:Build Logic:Unknown property implementation': ['Unknown property implementation'],

        // Build Definition
        'Gradle:Build Definition:Cannot write to reserved location': ['Cannot write to reserved location'],
        'Gradle:Build Definition:Input file does not exist': ['Input file does not exist'],
        'Gradle:Build Definition:Property has unsupported value': ['Property has unsupported value'],
        'Gradle:Build Definition:Property is not writable': ['Property is not writable'],
        'Gradle:Build Definition:Unexpected input file type': ['Unexpected input file type'],
        'Gradle:Build Definition:Running task ValidatePlugins with an unsupported Java Toolchain': ['Running task ValidatePlugins with an unsupported Java Toolchain'],
        'Gradle:Build Definition:Using task ValidatePlugins without applying the Java Toolchain plugin': ['Using task ValidatePlugins without applying the Java Toolchain plugin'],
        'Gradle:Build Definition:Value not set': ['Value not set'],

        // Build Logic: DCL schema building issues
        'Gradle:Build Logic:A hidden declaration is used in definition': ['A hidden declaration is used in definition'],
        'Gradle:Build Logic:Declaration is both visible and hidden': ['Declaration is both visible and hidden'],
        'Gradle:Build Logic:Illegal usage of type parameter bound by class': ['Illegal usage of type parameter bound by class'],
        'Gradle:Build Logic:Illegal variance in parameterized type usage': ['Illegal variance in parameterized type usage'],
        'Gradle:Build Logic:Non-classifiable type': ['Non-classifiable type'],
        'Gradle:Build Logic:Non-public members in safe feature API': ['Non-public members in safe feature API'],
        'Gradle:Build Logic:Schema building failure': ['Schema building failure'],
        'Gradle:Build Logic:Unit-adding function with lambda': ['Unit-adding function with lambda'],
        'Gradle:Build Logic:Unrecognized member': ['Unrecognized member'],
        'Gradle:Build Logic:Unsafe Java bean property in safe feature API': ['Unsafe Java bean property in safe feature API'],
        'Gradle:Build Logic:Unsafe hidden members in safe feature API': ['Unsafe hidden members in safe feature API'],
        'Gradle:Build Logic:Unsafe injected service property in safe feature API': ['Unsafe injected service property in safe feature API'],
        'Gradle:Build Logic:Unsafe non-abstract member in safe feature API': ['Unsafe non-abstract member in safe feature API'],
        'Gradle:Build Logic:Unsafe non-interface type in safe feature API': ['Unsafe non-interface type in safe feature API'],
        'Gradle:Build Logic:Unsafe non-pure function in safe feature API': ['Unsafe non-pure function in safe feature API'],
        'Gradle:Build Logic:Unsupported generic container type': ['Unsupported generic container type'],
        'Gradle:Build Logic:Unsupported map factory': ['Unsupported map factory'],
        'Gradle:Build Logic:Unsupported nullable read-only property': ['Unsupported nullable read-only property'],
        'Gradle:Build Logic:Unsupported nullable type': ['Unsupported nullable type'],
        'Gradle:Build Logic:Unsupported pair factory': ['Unsupported pair factory'],
        'Gradle:Build Logic:Unsupported type parameter as container type': ['Unsupported type parameter as container type'],
        'Gradle:Build Logic:Unsupported vararg type': ['Unsupported vararg type'],

        // dependency resolution failures
        'Dependencies:Graph Resolution:Configuration selected by name is not compatible': ['Configuration selected by name is not compatible'],
        'Dependencies:Graph Resolution:Configuration selected by name is not consumable': ['Configuration selected by name is not consumable'],
        'Dependencies:Graph Resolution:Configuration selected by name does not exist': ['Configuration selected by name does not exist'],
        'Dependencies:Graph Resolution:Multiple variants exist that would match the request': ['Multiple variants exist that would match the request'],
        'Dependencies:Graph Resolution:No variants exist that would match the request': ['No variants exist that would match the request'],
        'Dependencies:Graph Resolution:No variants exist with capabilities that would match the request': ['No variants exist with capabilities that would match the request'],
        'Dependencies:Graph Resolution:No version satisfies the constraints' : ['No version satisfies the constraints'],
        'Dependencies:Graph Resolution:Module rejected due to a capability conflict' : ['Module rejected due to a capability conflict'],

        'Dependencies:Artifact Resolution:Multiple artifact transforms exist that would satisfy the request': ['Multiple artifact transforms exist that would satisfy the request'],
        'Dependencies:Artifact Resolution:No artifacts exist that would match the request': ['No artifacts exist that would match the request'],
        'Dependencies:Artifact Resolution:Multiple artifacts exist that would match the request': ['Multiple artifacts exist that would match the request'],
        'Dependencies:Artifact Resolution:Unknown artifact selection failure': ['Unknown artifact selection failure'],

        'Dependencies:Graph Resolution:Incompatible nodes of a single component were selected': ['Incompatible nodes of a single component were selected'],

        'Dependencies:Graph Resolution:Unknown resolution failure': ['Unknown resolution failure'],


        // integration test problems
        'Gradle:Deprecation:Some indirect deprecation': ['Some indirect deprecation'],
        'Gradle:Deprecation:Some invocation feature': ['Some invocation feature'],
        'Gradle:Deprecation:Thing has been deprecated.': ['Thing has been deprecated.'],
        'Gradle:Deprecation:Typed task': ['Typed task'],
        'Gradle:Deprecation:test problem': ['test problem'],
        'generic:deprecation:plugin': ['DisplayName'],
        'generic:spawned': ['problem from spawned thread'],
        'generic:type': ['label'],
        'issues:finished': ['task finished'],
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
        'sample-problems:prototype-project': ['Project is a prototype'],
        'scripts:multiple-scripts': ['Multiple script files in one directory'],
    ]
}
