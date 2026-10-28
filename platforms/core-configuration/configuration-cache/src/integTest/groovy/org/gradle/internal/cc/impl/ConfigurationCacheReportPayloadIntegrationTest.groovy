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

package org.gradle.internal.cc.impl

import groovy.json.JsonOutput
import groovy.json.JsonSlurper

/**
 * Pins the diagnostics payload of the configuration cache report, so that changes to how problems are modeled
 * internally do not change or grow what the report carries.
 */
class ConfigurationCacheReportPayloadIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def "report diagnostics carry only the report fields"() {
        given:
        buildFile """
            abstract class TaskWithTaskField extends DefaultTask {
                @Internal
                Task otherTask

                @TaskAction
                void run() {}
            }

            def help = tasks.named('help')
            tasks.register('taskField', TaskWithTaskField) {
                otherTask = help.get()
            }

            tasks.register('projectAtExecution') {
                doLast {
                    println(project.name)
                }
            }

            tasks.register('incompatible') {
                notCompatibleWithConfigurationCache("Reasons")
            }

            gradle.buildFinished { }

            println(System.getProperty('payload.property'))
        """

        when:
        configurationCacheRunLenient 'taskField', 'projectAtExecution', 'incompatible'

        then:
        def diagnostics = problems.htmlReport(output).diagnostics

        diagnostics.every { REPORT_FIELDS.containsAll(it.keySet()) }
        normalized(diagnostics) == (new JsonSlurper().parseText(EXPECTED_DIAGNOSTICS) as List<Map<String, Object>>).sort { JsonOutput.toJson(it) }
    }

    private static final Set<String> REPORT_FIELDS = ['input', 'problem', 'incompatibleTask', 'trace', 'documentationLink', 'error'] as Set

    private static final String EXPECTED_DIAGNOSTICS = '''
        [
            {
                "incompatibleTask": [
                    {
                        "text": "task "
                    },
                    {
                        "name": ":incompatible"
                    },
                    {
                        "text": " of type "
                    },
                    {
                        "name": "org.gradle.api.DefaultTask"
                    },
                    {
                        "text": " is incompatible with the configuration cache. Reason: Reasons."
                    }
                ],
                "trace": [
                    {
                        "kind": "Task",
                        "path": ":incompatible",
                        "type": "org.gradle.api.DefaultTask"
                    }
                ],
                "documentationLink": "https://docs.gradle.org/current/userguide/configuration_cache_debugging.html#config_cache:task_opt_out"
            },
            {
                "input": [
                    {
                        "text": "system property "
                    },
                    {
                        "name": "payload.property"
                    }
                ],
                "trace": [
                    {
                        "kind": "BuildLogic",
                        "location": "build file 'build.gradle'"
                    }
                ],
                "documentationLink": "https://docs.gradle.org/current/userguide/configuration_cache_requirements.html#config_cache:requirements:reading_sys_props_and_env_vars"
            },
            {
                "problem": [
                    {
                        "text": "cannot "
                    },
                    {
                        "text": "serialize"
                    },
                    {
                        "text": " object of type "
                    },
                    {
                        "name": "org.gradle.configuration.Help"
                    },
                    {
                        "text": ", a subtype of "
                    },
                    {
                        "name": "org.gradle.api.Task"
                    },
                    {
                        "text": ","
                    },
                    {
                        "text": " as these are not supported with the configuration cache."
                    }
                ],
                "trace": [
                    {
                        "kind": "Field",
                        "name": "otherTask",
                        "declaringType": "TaskWithTaskField"
                    },
                    {
                        "kind": "Task",
                        "path": ":taskField",
                        "type": "TaskWithTaskField"
                    }
                ],
                "documentationLink": "https://docs.gradle.org/current/userguide/configuration_cache_requirements.html#config_cache:requirements:task_access",
                "error": {
                    "summary": null,
                    "hasStackTrace": false
                }
            },
            {
                "problem": [
                    {
                        "text": "invocation of "
                    },
                    {
                        "name": "Task.project"
                    },
                    {
                        "text": " at execution time is unsupported with the configuration cache."
                    }
                ],
                "trace": [
                    {
                        "kind": "BuildLogic",
                        "location": "build file 'build.gradle'"
                    }
                ],
                "documentationLink": "https://docs.gradle.org/current/userguide/configuration_cache_requirements.html#config_cache:requirements:use_project_during_execution",
                "error": {
                    "summary": [
                        {
                            "text": "at "
                        },
                        {
                            "name": "build.gradle:17"
                        }
                    ],
                    "hasStackTrace": true
                }
            },
            {
                "problem": [
                    {
                        "text": "registration of listener on "
                    },
                    {
                        "name": "Gradle.buildFinished"
                    },
                    {
                        "text": " is unsupported"
                    }
                ],
                "trace": [
                    {
                        "kind": "BuildLogic",
                        "location": "build file 'build.gradle'"
                    }
                ],
                "documentationLink": "https://docs.gradle.org/current/userguide/configuration_cache_requirements.html#config_cache:requirements:build_listeners",
                "error": {
                    "summary": [
                        {
                            "text": "at "
                        },
                        {
                            "name": "build.gradle:25"
                        }
                    ],
                    "hasStackTrace": true
                }
            }
        ]
    '''

    /**
     * The diagnostics in a stable order, with the parts that vary between runs replaced: the Gradle version in
     * documentation links, the stack frame in error summaries, which is reduced to its file and line, and the stack trace itself.
     */
    private static List<Map<String, Object>> normalized(List<Map<String, Object>> diagnostics) {
        def entries = diagnostics.collect { diagnostic ->
            diagnostic.collectEntries { key, value ->
                switch (key) {
                    case 'documentationLink':
                        return [(key): (value as String).replaceFirst('docs\\.gradle\\.org/[^/]+/', 'docs.gradle.org/current/')]
                    case 'error':
                        def error = value as Map<String, Object>
                        def summary = (error.summary as List<Map<String, Object>>)?.collect { fragment ->
                            fragment.name == null ? fragment : [name: (fragment.name as String).replaceFirst('^.*[/\\\\]([^/\\\\]+:\\d+)\\)$', '$1')]
                        }
                        return [(key): [summary: summary, hasStackTrace: error.parts != null]]
                    default:
                        return [(key): value]
                }
            }
        }
        return entries.sort { JsonOutput.toJson(it) }
    }
}
