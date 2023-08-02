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

package org.gradle.plugins.ide.idea

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.fixtures.TestResources
import org.gradle.plugins.ide.AbstractIdeIntegrationSpec
import org.gradle.plugins.ide.fixtures.IdeaFixtures
import org.junit.Rule

class IdeaJavaLanguageSettingsIntegrationTest extends AbstractIdeIntegrationSpec {
    @Rule
    public final TestResources testResources = new TestResources(testDirectoryProvider)

    def setup() {
        settingsFile << """
rootProject.name = 'root'
include ':child1', ':child2', ':child3'
"""
    }

    @ToBeFixedForConfigurationCache
    void "global sourceCompatibility results in project language level"() {
        given:
        buildFile << """
allprojects {
    apply plugin:'idea'
    apply plugin:'java'

    java.sourceCompatibility = "1.7"
}
"""
        when:
        succeeds "idea"

        then:
        ipr.languageLevel == "JDK_1_7"
        iml('root').languageLevel == null
        iml('child1').languageLevel == null
        iml('child2').languageLevel == null
        iml('child3').languageLevel == null
    }

    @ToBeFixedForConfigurationCache
    void "specific module languageLevel is exposed with derived language level"() {
        given:
        buildFile << """
allprojects {
    apply plugin:'idea'
    apply plugin:'java'

    java.sourceCompatibility = 1.6
}

project(':child1') {
    java.sourceCompatibility = 1.7
}

project(':child2') {
    java.sourceCompatibility = 1.5
}

project(':child3') {
    java.sourceCompatibility = 1.8
}
"""
        when:
        succeeds "idea"

        then:
        ipr.languageLevel == "JDK_1_8"
        iml('root').languageLevel == "JDK_1_6"
        iml("child1").languageLevel == "JDK_1_7"
        iml("child2").languageLevel == "JDK_1_5"
        iml("child3").languageLevel == null
    }

    @ToBeFixedForConfigurationCache
    void "use project language level not source language level and target bytecode level when explicitly set"() {
        given:
        buildFile << """
allprojects {
    apply plugin:'idea'
    apply plugin:'java'

    java.sourceCompatibility = 1.4
    java.targetCompatibility = 1.4
}

idea {
    project {
        jdkName   = 1.8
        languageLevel = 1.7
    }
}

project(':child1') {
    java.sourceCompatibility = 1.6
    java.targetCompatibility = 1.6
}

project(':child2') {
    java.sourceCompatibility = 1.5
    java.targetCompatibility = 1.5
}

project(':child3') {
    java.sourceCompatibility = 1.7
    java.targetCompatibility = 1.8
}
"""
        when:
        succeeds "idea"

        then:
        ipr.languageLevel == "JDK_1_7"
        ipr.jdkName == "1.8"
        iml('root').languageLevel == "JDK_1_4"
        iml('child1').languageLevel == "JDK_1_6"
        iml('child2').languageLevel == "JDK_1_5"
        iml('child3').languageLevel == null
        ipr.bytecodeTargetLevel.@target == '1.8'
        ipr.bytecodeTargetLevel.children().size() == 3
        ipr.bytecodeTargetLevel.module.find { it.@name == "root" }.@target == "1.4"
        ipr.bytecodeTargetLevel.module.find { it.@name == "child1" }.@target == "1.6"
        ipr.bytecodeTargetLevel.module.find { it.@name == "child2" }.@target == "1.5"
        !ipr.bytecodeTargetLevel.module.find { it.@name == "child3" }

        when:
        succeeds "idea"

        then:
        ipr.bytecodeTargetLevel.children().size() == 3

        ipr.bytecodeTargetLevel.module.find { it.@name == "root" }.@target == "1.4"
        ipr.bytecodeTargetLevel.module.find { it.@name == "child1" }.@target == "1.6"
        ipr.bytecodeTargetLevel.module.find { it.@name == "child2" }.@target == "1.5"
        !ipr.bytecodeTargetLevel.module.find { it.@name == "child3" }

        when:
        buildFile.text = """
        allprojects {
            apply plugin:'idea'
            apply plugin:'java'

            java.sourceCompatibility = 1.4
            java.targetCompatibility = 1.4
        }
        """
        and:
        succeeds "idea"

        then:
        ipr.bytecodeTargetLevel.children().size() == 0
    }

    @ToBeFixedForConfigurationCache
    void "uses subproject sourceCompatibility even if root project does not apply java plugin"() {
        buildFile << """
allprojects {
    apply plugin: 'idea'
}
subprojects {
    apply plugin:'java'
    java.sourceCompatibility = 1.7
}
"""

        when:
        succeeds "idea"

        then:
        ipr.languageLevel == "JDK_1_7"
        iml('child1').languageLevel == null
        iml('child2').languageLevel == null
        iml('child3').languageLevel == null
    }

    @ToBeFixedForConfigurationCache
    void "module languageLevel always exposed when no idea root project found"() {
        buildFile << """
subprojects {
    apply plugin:'java'
    apply plugin: 'idea'
    java.sourceCompatibility = 1.7
}
"""

        when:
        succeeds "idea"

        then:
        iml('child1').languageLevel == "JDK_1_7"
        iml('child2').languageLevel == "JDK_1_7"
        iml('child3').languageLevel == "JDK_1_7"
    }


    @ToBeFixedForConfigurationCache
    def "project bytecodeLevel set explicitly for same java versions"() {
        given:
        settingsFile << """
rootProject.name = "root"
include 'subprojectA'
include 'subprojectB'
include 'subprojectC'
"""

        buildFile << """
allprojects {
    apply plugin: 'java'
    apply plugin: 'idea'
    java.targetCompatibility = '1.6'
}

idea {
    project {
        jdkName = "1.6"
    }
}

"""

        when:
        executer.withTasks("idea").run()

        then:
        ipr.bytecodeTargetLevel.size() == 1
        ipr.bytecodeTargetLevel.@target == "1.6"
    }

    @ToBeFixedForConfigurationCache
    def "explicit project target level when module version differs from project java sdk"() {
        given:
        settingsFile << """
rootProject.name = "root"
include 'subprojectA'
include 'subprojectB'
include 'subprojectC'
"""

        buildFile << """
allprojects {
    apply plugin: 'java'
    apply plugin: 'idea'
    java.targetCompatibility = '1.7'
}

idea {
    project {
        jdkName = "1.8"
    }
}
"""

        when:
        executer.withTasks("idea").run()

        then:
        ipr.bytecodeTargetLevel.size() == 1
        ipr.bytecodeTargetLevel.@target == "1.7"
    }

    @ToBeFixedForConfigurationCache
    def "target bytecode version set if differs from calculated idea project bytecode version"() {
        given:
        settingsFile << """
rootProject.name = "root"
include 'subprojectA'
"""

        buildFile << """
allprojects {
    apply plugin: 'java'
    apply plugin: 'idea'
}

project(':') {
    java.targetCompatibility = 1.8
}

project(':subprojectA') {
    java.targetCompatibility = 1.7
}
"""

        when:
        executer.withTasks("idea").run()

        then:
        ipr.bytecodeTargetLevel.size() == 1
        ipr.bytecodeTargetLevel.module.find { it.@name == "subprojectA" }.@target == "1.7"
    }

    @ToBeFixedForConfigurationCache
    def "language level set if differs from calculated idea project language level"() {
        given:
        settingsFile << """
rootProject.name = "root"
include 'child1'
"""

        buildFile << """
allprojects {
    apply plugin: 'idea'
    apply plugin: 'java'
}

project(':') {
    java.sourceCompatibility = 1.8
}

project(':child1') {
    java.sourceCompatibility = 1.7
}
"""

        when:
        executer.withTasks("idea").run()

        then:
        iml('child1').languageLevel == "JDK_1_7"
    }

    @ToBeFixedForConfigurationCache
    def "language level set if root has no idea plugin applied"() {
        given:
        settingsFile << """
rootProject.name = "root"
include 'child1'
"""

        buildFile << """
allprojects {
    apply plugin: 'java'
    java.sourceCompatibility = 1.7
}

project(':child1') {
    apply plugin: 'idea'
}
"""

        when:
        executer.withTasks("idea").run()

        then:
        iml('child1').languageLevel == "JDK_1_7"
    }

    @ToBeFixedForConfigurationCache
    def "can have module specific bytecode version"() {
        given:
        settingsFile << """
rootProject.name = "root"
include 'subprojectA'
include 'subprojectB'
include 'subprojectC'
include 'subprojectD'
"""

        buildFile << """
configure(project(':subprojectA')) {
    apply plugin: 'java'
    apply plugin: 'idea'
    java.targetCompatibility = '1.6'
}

configure(project(':subprojectB')) {
    apply plugin: 'java'
    apply plugin: 'idea'
    java.targetCompatibility = '1.7'
}

configure(project(':subprojectC')) {
    apply plugin: 'java'
    apply plugin: 'idea'
    java.targetCompatibility = '1.8'
}

configure(project(':subprojectD')) {
    apply plugin: 'idea'
}

apply plugin:'idea'
idea {
    project {
        jdkName = "1.8"
    }
}

"""

        when:
        executer.withTasks("idea").run()

        then:
        ipr.bytecodeTargetLevel.size() == 1
        ipr.bytecodeTargetLevel.module.size() == 2
        ipr.bytecodeTargetLevel.module.find { it.@name == "subprojectA" }.@target == "1.6"
        ipr.bytecodeTargetLevel.module.find { it.@name == "subprojectB" }.@target == "1.7"
    }

    @ToBeFixedForConfigurationCache
    void "language levels specified in properties files are ignored"() {
        given:
        file('gradle.properties') << """
sourceCompatibility=1.3
targetCompatibility=1.3
java.sourceCompatibility=1.3
java.targetCompatibility=1.3
"""

        buildFile << """
allprojects {
    apply plugin:'idea'
    apply plugin:'java'
}
"""
        when:
        succeeds "idea"

        then:
        ipr.languageLevel == JavaVersion.current().name().replace('VERSION', 'JDK')
        iml('root').languageLevel == null
        iml('child1').languageLevel == null
        iml('child2').languageLevel == null
        iml('child3').languageLevel == null
    }

    def getIpr() {
        return IdeaFixtures.parseIpr(file("root.ipr"))
    }

    def iml(String name = 'root') {
        if (name == 'root') {
            return parseIml('root.iml')
        }
        return parseIml("${name}/${name}.iml")
    }
}
