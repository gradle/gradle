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

package org.gradle.features.internal

import org.gradle.features.internal.builders.Language
import org.gradle.features.internal.builders.TestScenarioBuilder

import static org.gradle.features.internal.builders.PluginType.NO_PLUGIN
import org.gradle.test.fixtures.file.TestFile
import spock.lang.Specification
import spock.lang.TempDir

class TestScenarioBuilderTest extends Specification {
    @TempDir
    File tempDirFile

    TestFile getTempDir() { new TestFile(tempDirFile) }

    def "builds simple project type scenario"() {
        given:
        def scenario = new TestScenarioBuilder()
        scenario.projectType("testProjectType") {}

        when:
        def pb = scenario.build(tempDir)

        then:
        new File(tempDir, "src/main/java/org/gradle/test/TestProjectTypeDefinition.java").exists()
        new File(tempDir, "src/main/java/org/gradle/test/TestProjectTypeImplPlugin.java").exists()
        new File(tempDir, "src/main/java/org/gradle/test/ProjectTypeRegistrationPlugin.java").exists()
        pb.pluginIds.containsKey("com.example.test-project-type-impl")
        pb.pluginIds.containsKey("com.example.test-software-ecosystem")
    }

    def "builds project type + feature scenario"() {
        given:
        def scenario = new TestScenarioBuilder()
        def type = scenario.projectType("testProjectType") {}
        scenario.projectFeature("feature") {
            plugin {
                bindsFeatureTo(type)
            }
        }

        when:
        def pb = scenario.build(tempDir)

        then:
        new File(tempDir, "src/main/java/org/gradle/test/TestProjectTypeDefinition.java").exists()
        new File(tempDir, "src/main/java/org/gradle/test/TestProjectTypeImplPlugin.java").exists()
        new File(tempDir, "src/main/java/org/gradle/test/FeatureDefinition.java").exists()
        new File(tempDir, "src/main/java/org/gradle/test/FeatureImplPlugin.java").exists()
        new File(tempDir, "src/main/java/org/gradle/test/ProjectTypeRegistrationPlugin.java").exists()
        pb.pluginIds.containsKey("com.example.test-project-type-impl")
        pb.pluginIds.containsKey("com.example.test-software-feature-impl")
        pb.pluginIds.containsKey("com.example.test-software-ecosystem")
    }

    def "builds multi-type scenario"() {
        given:
        def scenario = new TestScenarioBuilder()
        scenario.projectType("testProjectType") {}
        scenario.projectType("anotherProjectType") {}

        when:
        def pb = scenario.build(tempDir)

        then:
        new File(tempDir, "src/main/java/org/gradle/test/TestProjectTypeDefinition.java").exists()
        new File(tempDir, "src/main/java/org/gradle/test/AnotherProjectTypeDefinition.java").exists()
        pb.pluginIds.containsKey("com.example.test-project-type-impl")
        pb.pluginIds.containsKey("com.example.additional-type-impl-1")
    }

    def "builds scenario with explicit cross-references"() {
        given:
        def scenario = new TestScenarioBuilder()
        def type = scenario.projectType("testProjectType") {
            definition {
                buildModel("ModelType") { property "id", String }
                property "id", String
            }
        }
        scenario.projectFeature("feature") {
            definition {
                buildModel("FeatureModel") { property "text", String }
                property "text", String
            }
            plugin {
                bindToBuildModel()
                bindsFeatureTo(type.definition.fullyQualifiedBuildModelClassName)
            }
        }

        when:
        def pb = scenario.build(tempDir)
        def featureContent = new File(tempDir, "src/main/java/org/gradle/test/FeatureImplPlugin.java").text

        then:
        featureContent.contains("bindProjectFeatureToBuildModel")
        featureContent.contains("org.gradle.test.TestProjectTypeDefinition.ModelType")
    }

    def "feature with explicit binding uses specified type"() {
        given:
        def scenario = new TestScenarioBuilder()
        def type = scenario.projectType("testProjectType") {}
        scenario.projectFeature("feature") {
            plugin {
                bindsFeatureTo(type)
            }
        }

        when:
        def pb = scenario.build(tempDir)
        def featureContent = new File(tempDir, "src/main/java/org/gradle/test/FeatureImplPlugin.java").text

        then:
        featureContent.contains("TestProjectTypeDefinition.class")
    }

    def "feature with multiple binding targets generates multiple bind calls"() {
        given:
        def scenario = new TestScenarioBuilder()
        def typeA = scenario.projectType("typeA") {}
        def typeB = scenario.projectType("typeB") {}
        scenario.projectFeature("feature") {
            plugin {
                bindsFeatureTo(typeA)
                bindsFeatureTo(typeB)
            }
        }

        when:
        def pb = scenario.build(tempDir)
        def featureContent = new File(tempDir, "src/main/java/org/gradle/test/FeatureImplPlugin.java").text

        then:
        featureContent.contains("TypeADefinition.class")
        featureContent.contains("TypeBDefinition.class")
    }

    def "propagates top-level language"() {
        given:
        def scenario = new TestScenarioBuilder()
        scenario.language(Language.KOTLIN)
        def type = scenario.projectType("testProjectType") {}
        scenario.projectFeature("feature") {
            plugin {
                bindsFeatureTo(type)
            }
        }

        when:
        def pb = scenario.build(tempDir)

        then:
        new File(tempDir, "src/main/kotlin/org/gradle/test/TestProjectTypeImplPlugin.kt").exists()
        new File(tempDir, "src/main/kotlin/org/gradle/test/FeatureImplPlugin.kt").exists()
        new File(tempDir, "src/main/kotlin/org/gradle/test/ProjectFeatureRegistrationPlugin.kt").exists()
    }

    def "builds standalone plugin scenario"() {
        given:
        def scenario = new TestScenarioBuilder()
        scenario.projectType("testProjectType") {}
        scenario.plugin("MyStandalonePlugin") {}

        when:
        def pb = scenario.build(tempDir)

        then:
        new File(tempDir, "src/main/java/org/gradle/test/MyStandalonePlugin.java").exists()
        pb.pluginIds.containsKey("com.example.standalone-plugin-0")
        pb.pluginIds.containsKey("com.example.test-project-type-impl")
    }

    def "standalone plugin defaults to no bindings"() {
        given:
        def scenario = new TestScenarioBuilder()
        scenario.projectType("testProjectType") {}
        scenario.plugin("MyStandalonePlugin") {}

        when:
        scenario.build(tempDir)
        def content = new File(tempDir, "src/main/java/org/gradle/test/MyStandalonePlugin.java").text

        then:
        !content.contains("@BindsProjectType")
        !content.contains("@BindsProjectFeature")
    }

    def "standalone plugin is not registered in settings when it has no bindings"() {
        given:
        def scenario = new TestScenarioBuilder()
        scenario.projectType("testProjectType") {}
        scenario.plugin("MyStandalonePlugin") {}

        when:
        scenario.build(tempDir)
        def settingsContent = new File(tempDir, "src/main/java/org/gradle/test/ProjectTypeRegistrationPlugin.java").text

        then:
        !settingsContent.contains("MyStandalonePlugin")
    }

    def "standalone plugin propagates top-level language"() {
        given:
        def scenario = new TestScenarioBuilder()
        scenario.language(Language.KOTLIN)
        scenario.projectType("testProjectType") {}
        scenario.plugin("MyStandalonePlugin") {}

        when:
        scenario.build(tempDir)

        then:
        new File(tempDir, "src/main/kotlin/org/gradle/test/MyStandalonePlugin.kt").exists()
    }

    def "multiple standalone plugins get sequential IDs"() {
        given:
        def scenario = new TestScenarioBuilder()
        scenario.projectType("testProjectType") {}
        scenario.plugin("PluginA") {}
        scenario.plugin("PluginB") {}

        when:
        def pb = scenario.build(tempDir)

        then:
        pb.pluginIds.containsKey("com.example.standalone-plugin-0")
        pb.pluginIds.containsKey("com.example.standalone-plugin-1")
        pb.pluginIds["com.example.standalone-plugin-0"] == "PluginA"
        pb.pluginIds["com.example.standalone-plugin-1"] == "PluginB"
    }

    def "NO_PLUGIN suppresses plugin registration and ID assignment"() {
        given:
        def scenario = new TestScenarioBuilder()
        scenario.projectType("testProjectType") {
            plugin {
                type NO_PLUGIN
            }
        }

        when:
        def pb = scenario.build(tempDir)

        then:
        !pb.pluginIds.containsKey("com.example.test-project-type-impl")
        pb.pluginIds.containsKey("com.example.test-software-ecosystem")
    }

    def "NO_PLUGIN with standalone plugin for combined scenario"() {
        given:
        def scenario = new TestScenarioBuilder()
        scenario.projectType("testProjectType") {
            plugin {
                type NO_PLUGIN
            }
        }
        scenario.plugin("CombinedPlugin") {}

        when:
        def pb = scenario.build(tempDir)

        then:
        !pb.pluginIds.containsKey("com.example.test-project-type-impl")
        pb.pluginIds.containsKey("com.example.standalone-plugin-0")
        new File(tempDir, "src/main/java/org/gradle/test/TestProjectTypeDefinition.java").exists()
        new File(tempDir, "src/main/java/org/gradle/test/CombinedPlugin.java").exists()
    }

    def "feature auto-creates default project type when none specified"() {
        given:
        def scenario = new TestScenarioBuilder()
        scenario.projectFeature("feature") {
            plugin {
                bindsFeatureTo("TestProjectTypeDefinition")
            }
        }

        when:
        def pb = scenario.build(tempDir)

        then:
        new File(tempDir, "src/main/java/org/gradle/test/TestProjectTypeDefinition.java").exists()
        new File(tempDir, "src/main/java/org/gradle/test/TestProjectTypeImplPlugin.java").exists()
        new File(tempDir, "src/main/java/org/gradle/test/FeatureDefinition.java").exists()
    }
}
