/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Unroll

class ProjectDependencyPreferenceIntegrationTest extends AbstractIntegrationSpec {
    /**
     * This is an attempt to go over the permutations described in the dev forum:
     * https://groups.google.com/d/msg/gradle-dev/vBzbu4_6Zbw/l_HXLBoJDQAJ
     */
    @Unroll
    void "project dependency (#versionInProject) vs external dependency (#versionExternal) resolved in favor of #winner, when preferProjectModules=#preferProjectModules and forcedVersion=#forcedVersion"() {
        when:

        mavenRepo.module("org.utils", "api", versionExternal).publish()
        if (forcedVersion != null && forcedVersion != 'project' && forcedVersion != versionExternal) {
            mavenRepo.module("org.utils", "api", forcedVersion).publish()
        }
        mavenRepo.module("org.utils", "api-ext", '1.0').dependsOn("org.utils", "api", versionExternal).publish()
        settingsFile << 'include "api", "moduleA", "moduleB"'

        def projectPrioritySetting = "preferProjectModules = $preferProjectModules"
        String apiDependency = forcedVersion == null && forcedVersion != 'project' ?
            'conf project(":api")' : "conf ('org.utils:api:$forcedVersion') { force = true }"

        String substitutionSetting = forcedVersion == 'project' ?
            "dependencySubstitution { substitute module('org.utils:api') with project(':api') }" : ''

        buildFile << """
project(":api") {
    group "org.utils"
    version = $versionInProject
    $common
    configurations.create("default").extendsFrom(configurations.conf)
}

project(":moduleA") {
    $common
    configurations.create("default").extendsFrom(configurations.conf)
    dependencies {
        conf project(":api")
    }
}

project(":moduleB") {
    $common
    configurations.create("default").extendsFrom(configurations.conf)

    dependencies {
        conf "org.utils:api:1.0"
        conf "org.utils:api-ext:1.0"
        conf project(":moduleA")
        $apiDependency
    }

    configurations.conf.resolutionStrategy {
        $projectPrioritySetting
        $substitutionSetting
    }

    task check {
        doLast {
            def deps = configurations.conf.incoming.resolutionResult.allDependencies as List
            def apiProjectDependency = deps.find {
                it instanceof org.gradle.api.artifacts.result.ResolvedDependencyResult &&
                it.requested.matchesStrictly(projectId(":api"))
            }
            assert apiProjectDependency && apiProjectDependency.selected.componentId == $winner
        }
    }
}

def moduleId(String group, String name, String version) {
    return org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier.newId(group, name, version)
}

def projectId(String projectPath) {
    return org.gradle.internal.component.local.model.DefaultProjectComponentIdentifier.newProjectId(rootProject.subprojects.find { it.path == projectPath})
}
"""

        then:
        succeeds('moduleB:check') != null

        where:
        versionInProject | versionExternal | winner                                | preferProjectModules | forcedVersion
        "1.6"            | "2.0"           | 'projectId(":api")'                   | true                 | null
        "1.6"            | "2.0"           | 'moduleId("org.utils", "api", "2.0")' | false                | null
        "3.0"            | "2.0"           | 'moduleId("org.utils", "api", "1.5")' | true                 | '1.5'
        "1.6"            | "2.0"           | 'projectId(":api")'                   | false                | 'project'
        "1.6"            | "1.6"           | 'projectId(":api")'                   | true                 | null
        "1.6"            | "1.6"           | 'projectId(":api")'                   | false                | null
    }

    String getCommon() {
"""
    configurations { conf }
    repositories {
        maven { url "${mavenRepo.uri}" }
    }
    task resolveConf { doLast { configurations.conf.files } }

    //resolving the configuration at the end:
    gradle.startParameter.taskNames += 'resolveConf'
"""
    }

}
