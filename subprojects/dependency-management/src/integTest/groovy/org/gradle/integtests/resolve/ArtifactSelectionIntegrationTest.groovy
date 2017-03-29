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

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.FluidDependenciesResolveRunner
import org.junit.runner.RunWith

@RunWith(FluidDependenciesResolveRunner)
class ArtifactSelectionIntegrationTest extends AbstractHttpDependencyResolutionTest {
    def setup() {
        settingsFile << """
            rootProject.name = 'root'
            include 'lib'
            include 'ui'
            include 'app'
        """

        buildFile << """
def artifactType = Attribute.of('artifactType', String)
def usage = Attribute.of('usage', String)
def buildType = Attribute.of('buildType', String)
def flavor = Attribute.of('flavor', String)

allprojects {
    repositories {
        ivy { url '${ivyHttpRepo.uri}' }
    }
    dependencies {
        attributesSchema {
           attribute(usage).compatibilityRules.assumeCompatibleWhenMissing()
           attribute(buildType).compatibilityRules.assumeCompatibleWhenMissing()
           attribute(flavor)
        }
    }
    configurations {
        compile {
            attributes { attribute(usage, 'api') }
        }
    }
    task utilJar {
        outputs.file("\${project.name}-util.jar")
    }
    task jar {
        outputs.file("\${project.name}.jar")
    }
    task utilClasses {
        outputs.file("\${project.name}-util.classes")
    }
    task classes {
        outputs.file("\${project.name}.classes")
    }
    task dir {
        outputs.file("\${project.name}")
    }
    task utilDir {
        outputs.file("\${project.name}-util")
    }
}
"""
    }

    def "selects artifacts and files whose format matches the requested"() {
        given:
        def m1 = ivyHttpRepo.module('org', 'test', '1.0')
                    .artifact(name: 'some-jar', type: 'jar')
                    .publish()
        def m2 = ivyHttpRepo.module('org', 'test2', '1.0')
                    .artifact(name: 'some-classes', type: 'classes')
                    .publish()

        buildFile << """
            project(':lib') {
                dependencies {
                    compile utilJar.outputs.files
                    compile utilClasses.outputs.files
                    compile utilDir.outputs.files
                    compile 'org:test:1.0'
                    compile 'org:test2:1.0'
                }
                configurations {
                    compile {
                        outgoing {
                            variants {
                                jarFormat {
                                    artifact file: file('lib.jar'), builtBy: tasks.jar
                                }
                                classesFormat {
                                    artifact file: file('lib.classes'), builtBy: tasks.classes
                                }
                                dirFormat {
                                    artifact file: file('lib'), builtBy: tasks.dir
                                }
                            }
                        }
                    }
                }
            }
            project(':ui') {
                artifacts {
                    compile file: file('ui.jar'), builtBy: jar
                }
            }

            project(':app') {
                def otherAttributeRequired = Attribute.of('otherAttributeRequired', String)
                def otherAttributeOptional = Attribute.of('otherAttributeOptional', String)

                dependencies {
                    compile project(':lib'), project(':ui')
                    
                    attributesSchema {
                        attribute(otherAttributeRequired)
                        attribute(otherAttributeOptional) {
                            compatibilityRules.assumeCompatibleWhenMissing()
                        }
                    }
                }

                task resolve {
                    inputs.files configurations.compile.incoming.artifactView {
                        attributes { it.attribute(artifactType, 'jar') }
                    }.files
                    doLast {
                        // Get a view specifying the default type
                        def defaultView = configurations.compile.incoming.artifactView {
                            attributes { 
                                it.attribute(artifactType, 'jar') 
                            }
                        }
                        assert defaultView.files.collect { it.name } == ['lib-util.jar', 'lib.jar', 'ui.jar', 'some-jar-1.0.jar']
                        assert defaultView.artifacts.collect { it.id.displayName }  == ['lib-util.jar', 'lib.jar (project :lib)', 'ui.jar (project :ui)', 'some-jar.jar (org:test:1.0)']

                        // Get a view with additional optional attribute
                        def optionalAttributeView =  configurations.compile.incoming.artifactView {
                            attributes { 
                                it.attribute(artifactType, 'jar')
                                it.attribute(otherAttributeOptional, 'anything') 
                            }
                        }
                        assert optionalAttributeView.files.collect { it.name } == ['lib-util.jar', 'lib.jar', 'ui.jar', 'some-jar-1.0.jar']
                        assert optionalAttributeView.artifacts.collect { it.id.displayName }  == ['lib-util.jar', 'lib.jar (project :lib)', 'ui.jar (project :ui)', 'some-jar.jar (org:test:1.0)']
                    
                        // Get a view with additional required attribute
                        def requiredAttributeView =  configurations.compile.incoming.artifactView {
                            attributes { 
                                it.attribute(artifactType, 'jar')
                                it.attribute(otherAttributeRequired, 'anything') 
                            }
                        }
                        assert requiredAttributeView.files.collect { it.name } == []
                        assert requiredAttributeView.artifacts.collect { it.id.displayName }  == []
                    }
                }
            }
        """

        m1.ivy.expectGet()
        m2.ivy.expectGet()
        m1.getArtifact(name: 'some-jar', type: 'jar').expectGet()

        expect:
        succeeds "resolve"
        // Currently builds all file dependencies
        executed ":lib:jar", ":lib:utilClasses", ":lib:utilDir", ":lib:utilJar", ":ui:jar", ":app:resolve"
    }

    def "can create a view that selects different artifacts from the same dependency graph"() {
        given:
        def m1 = ivyHttpRepo.module('org', 'test', '1.0')
                    .artifact(name: 'some-jar', type: 'jar')
                    .publish()
        def m2 = ivyHttpRepo.module('org', 'test2', '1.0')
                    .artifact(name: 'some-classes', type: 'classes')
                    .publish()

        buildFile << """
            project(':lib') {
                dependencies {
                    compile utilJar.outputs.files
                    compile utilClasses.outputs.files
                    compile utilDir.outputs.files
                    compile 'org:test:1.0'
                    compile 'org:test2:1.0'
                }
                configurations {
                    compile {
                        outgoing {
                            variants {
                                jarFormat {
                                    artifact file: file('lib.jar'), builtBy: tasks.jar
                                }
                                classesFormat {
                                    artifact file: file('lib.classes'), builtBy: tasks.classes
                                }
                                dirFormat {
                                    artifact file: file('lib'), builtBy: tasks.dir
                                }
                            }
                        }
                    }
                }
            }
            project(':ui') {
                artifacts {
                    compile file: file('ui.classes'), builtBy: classes
                }
            }

            project(':app') {
                configurations {
                    compile {
                        attributes { attribute(artifactType, 'jar') }
                    }
                }

                dependencies {
                    compile project(':lib'), project(':ui')
                }

                task resolve {
                    def view = configurations.compile.incoming.artifactView { 
                        attributes { it.attribute(artifactType, 'classes') } 
                    }
                    inputs.files view.files
                    doLast {
                        assert view.files.collect { it.name } == ['lib-util.classes', 'lib.classes', 'ui.classes', 'some-classes-1.0.classes']
                        assert view.artifacts.collect { it.id.displayName } == ['lib-util.classes', 'lib.classes (project :lib)', 'ui.classes (project :ui)', 'some-classes.classes (org:test2:1.0)']
                    }
                }
            }
        """

        m1.ivy.expectGet()
        m2.ivy.expectGet()
        m2.getArtifact(name: 'some-classes', type: 'classes').expectGet()

        expect:
        succeeds "resolve"
        // Currently builds all file dependencies
        executed ":lib:classes", ":lib:utilClasses", ":lib:utilDir", ":lib:utilJar", ":ui:classes", ":app:resolve"
    }

    def "applies consumers compatibility and disambiguation rules when selecting variant"() {
        buildFile << """
class BuildTypeCompatibilityRule implements AttributeCompatibilityRule<String> {
    void execute(CompatibilityCheckDetails<String> details) {
        if (details.consumerValue == "debug" && details.producerValue == "profile") { 
            details.compatible()
        }    
    }
}
class FlavorSelectionRule implements AttributeDisambiguationRule<String> {
    void execute(MultipleCandidatesDetails<String> details) {
        if (details.candidateValues.contains('tasty')) { 
            details.closestMatch('tasty')
        }
    }
}

dependencies.attributesSchema {
    attribute(buildType) {
        compatibilityRules.add(BuildTypeCompatibilityRule)
    }
    attribute(flavor) {
        compatibilityRules.assumeCompatibleWhenMissing()
        disambiguationRules.add(FlavorSelectionRule)
    }
}

dependencies {
    compile project(':lib')
}

project(':lib') {
    configurations {
        compile {
            outgoing {
                variants {
                    var1 {
                        artifact file('a1.jar')
                        attributes.attribute(buildType, 'profile')
                        attributes.attribute(flavor, 'bland')
                    }
                    var2 {
                        artifact file('a2.jar')
                        attributes.attribute(buildType, 'profile')
                        attributes.attribute(flavor, 'tasty')
                    }
                    var3 {
                        artifact file('a3.jar')
                        attributes.attribute(buildType, 'debug')
                        attributes.attribute(flavor, 'bland')
                    }
                }
            }
        }
    }
}

task show {
    inputs.files configurations.compile
    doLast {
        def artifacts = configurations.compile.incoming.artifactView {
            attributes { it.attribute(buildType, 'debug') }
        }.artifacts
        println "files: " + artifacts.collect { it.file.name }
        println "variants: " + artifacts.collect { it.variant.attributes }
    }
}
"""
        when:
        run 'show'

        then:
        outputContains("files: [a2.jar]")
        outputContains("variants: [{artifactType=jar, buildType=profile, flavor=tasty, usage=api}]")
    }

    def "applies producer's disambiguation rules when selecting variant"() {
        buildFile << """
class FlavorCompatibilityRule implements AttributeCompatibilityRule<String> {
    void execute(CompatibilityCheckDetails<String> details) {
        details.compatible()
    }
}
class FlavorSelectionRule implements AttributeDisambiguationRule<String> {
    void execute(MultipleCandidatesDetails<String> details) {
        if (details.candidateValues.contains('tasty')) { 
            details.closestMatch('tasty')
        }
    }
}

dependencies {
    compile project(':lib')
}

project(':lib') {
    dependencies.attributesSchema {
        attribute(flavor) {
            compatibilityRules.add(FlavorCompatibilityRule)
            disambiguationRules.add(FlavorSelectionRule)
        }
    }

    configurations {
        compile {
            outgoing {
                variants {
                    var1 {
                        artifact file('a1.jar')
                        attributes.attribute(buildType, 'release')
                        attributes.attribute(flavor, 'bland')
                    }
                    var2 {
                        artifact file('a2.jar')
                        attributes.attribute(buildType, 'debug')
                        attributes.attribute(flavor, 'bland')
                    }
                    var3 {
                        artifact file('a3.jar')
                        attributes.attribute(buildType, 'debug')
                        attributes.attribute(flavor, 'tasty')
                    }
                }
            }
        }
    }
}

task show {
    def artifacts = configurations.compile.incoming.artifactView {
        attributes { it.attribute(buildType, 'debug'); it.attribute(flavor, 'anything') }
    }.artifacts
    inputs.files artifacts.artifactFiles
    doLast {
        println "files: " + artifacts.collect { it.file.name }
        println "variants: " + artifacts.collect { it.variant.attributes }
    }
}
"""
        when:
        run 'show'

        then:
        outputContains("files: [a3.jar]")
        outputContains("variants: [{artifactType=jar, buildType=debug, flavor=tasty, usage=api}]")
    }

    def "applies producer's compatibility disambiguation rules for additional producer attributes when selecting variant"() {
        buildFile << """
class ExtraSelectionRule implements AttributeDisambiguationRule<String> {
    void execute(MultipleCandidatesDetails<String> details) {
        if (details.candidateValues.contains('good')) { 
            details.closestMatch('good')
        }
    }
}

def extra = Attribute.of('extra', String)

dependencies {
    compile project(':lib')
}

project(':lib') {
    dependencies.attributesSchema {
        attribute(extra) {
            compatibilityRules.assumeCompatibleWhenMissing()
            disambiguationRules.add(ExtraSelectionRule)
        }
    }
    
    configurations {
        compile {
            outgoing {
                variants {
                    var1 {
                        artifact file('a1.jar')
                        attributes.attribute(buildType, 'release')
                        attributes.attribute(extra, 'ok')
                    }
                    var2 {
                        artifact file('a2.jar')
                        attributes.attribute(buildType, 'debug')
                        attributes.attribute(extra, 'ok')
                    }
                    var3 {
                        artifact file('a3.jar')
                        attributes.attribute(buildType, 'debug')
                        attributes.attribute(extra, 'good')
                    }
                }
            }
        }
    }
}

task show {
    inputs.files configurations.compile
    doLast {
        def artifacts = configurations.compile.incoming.artifactView {
            attributes { it.attribute(buildType, 'debug') }
        }.artifacts
        println "files: " + artifacts.collect { it.file.name }
        println "variants: " + artifacts.collect { it.variant.attributes }
    }
}
"""
        when:
        run 'show'

        then:
        outputContains("files: [a3.jar]")
        outputContains("variants: [{artifactType=jar, buildType=debug, extra=good, usage=api}]")
    }

    def "can select the implicit variant of a configuration"() {
        buildFile << """

dependencies {
    compile project(':lib')
    compile project(':ui')
}

project(':lib') {
    configurations {
        legacy
        compile.extendsFrom legacy
    }
    artifacts {
        legacy file('a1.jar')
    }
    configurations.compile.outgoing {
        artifact file('a2.jar')
        attributes.attribute(buildType, 'debug')
        variants {
            var1 {
                artifact file('ignore-me.jar')
                attributes.attribute(buildType, 'release')
            }
            var2 {
                artifact file('ignore-me.jar')
                attributes.attribute(buildType, 'profile')
            }
        }
    }
}
project(':ui') {
    configurations {
        legacy
        compile.extendsFrom legacy
    }
    artifacts {
        legacy file('b1.jar')
        compile file('b2.jar')
    }
}

task show {
    def artifacts = configurations.compile.incoming.artifactView {
        attributes { it.attribute(buildType, 'debug') }
    }.artifacts
    inputs.files artifacts.artifactFiles
    doLast {
        println "files: " + artifacts.collect { it.file.name }
        println "variants: " + artifacts.collect { it.variant.attributes }
    }
}
"""
        when:
        run 'show'

        then:
        outputContains("files: [a2.jar, a1.jar, b2.jar, b1.jar]")
        outputContains("variants: [{artifactType=jar, buildType=debug, usage=api}, {artifactType=jar, buildType=debug, usage=api}, {artifactType=jar, usage=api}, {artifactType=jar, usage=api}]")
    }

    def "result includes consumer-provided variants"() {
        def m1 = ivyHttpRepo.module("org", "test", "1.0").publish()

        buildFile << """

class VariantArtifactTransform extends ArtifactTransform {
    List<File> transform(File input) {
        def output = new File(outputDirectory, "transformed-" + input.name)
        output << "transformed"
        return [output]         
    }
}

dependencies {
    compile files('test-lib.jar')
    compile project(':lib')
    compile project(':ui')
    compile 'org:test:1.0'
    registerTransform {
        to.attribute(usage, "transformed")
        artifactTransform(VariantArtifactTransform)
    }
}

project(':lib') {
    configurations {
        compile {
            attributes.attribute(buildType, 'debug')
            outgoing {
                variants {
                    var1 {
                        artifact file('a1.jar')
                        attributes.attribute(flavor, 'one')
                    }
                }
            }
        }
    }
}

project(':ui') {
    artifacts {
        compile file('b2.jar')
    }
}

task show {
    def artifacts = configurations.compile.incoming.artifactView {
        attributes {it.attribute(usage, 'transformed')}
    }.artifacts
    inputs.files artifacts.artifactFiles
    doLast {
        println "files: " + artifacts.collect { it.file.name }
        println "components: " + artifacts.collect { it.id.componentIdentifier.displayName }
        println "variants: " + artifacts.collect { it.variant.attributes }
    }
}
"""

        when:
        m1.ivy.expectGet()
        m1.jar.expectGet()
        run 'show'

        then:
        outputContains("files: [test-lib.jar, transformed-a1.jar, transformed-b2.jar, test-1.0.jar]")
        outputContains("components: [test-lib.jar, project :lib, project :ui, org:test:1.0]")
        outputContains("variants: [{artifactType=jar}, {artifactType=jar, buildType=debug, flavor=one, usage=transformed}, {artifactType=jar, usage=transformed}, {artifactType=jar}]")
    }

    def "can query the content of view before task graph is calculated"() {
        given:
        def m1 = ivyHttpRepo.module('org', 'test', '1.0')
                    .artifact(name: 'some-jar', type: 'jar')
                    .publish()
        def m2 = ivyHttpRepo.module('org', 'test2', '1.0')
                    .artifact(name: 'some-classes', type: 'classes')
                    .publish()

        buildFile << """
            project(':lib') {
                dependencies {
                    compile utilJar.outputs.files
                    compile utilClasses.outputs.files
                    compile utilDir.outputs.files
                    compile 'org:test:1.0'
                    compile 'org:test2:1.0'
                }
                configurations {
                    compile {
                        outgoing {
                            variants {
                                jarFormat {
                                    artifact file: file('lib.jar'), builtBy: tasks.jar
                                }
                                classesFormat {
                                    artifact file: file('lib.classes'), builtBy: tasks.classes
                                }
                                dirFormat {
                                    artifact file: file('lib'), builtBy: tasks.dir
                                }
                            }
                        }
                    }
                }
            }
            project(':ui') {
                artifacts {
                    compile file: file('ui.classes'), builtBy: classes
                }
            }

            project(':app') {
                configurations {
                    compile {
                        attributes { attribute(artifactType, 'jar') }
                    }
                }

                dependencies {
                    compile project(':lib'), project(':ui')
                }

                task resolve {
                    def files = configurations.compile.incoming.artifactView {
                        attributes { it.attribute(artifactType, 'classes') }
                    }.files
                    files.each { println it.name }
                    inputs.files files
                    doLast {
                        assert files.collect { it.name } == ['lib-util.classes', 'lib.classes', 'ui.classes', 'some-classes-1.0.classes']
                    }
                }
            }
        """

        m1.ivy.expectGet()
        m2.ivy.expectGet()
        m2.getArtifact(name: 'some-classes', type: 'classes').expectGet()

        expect:
        succeeds "resolve"
        // Currently builds all file dependencies
        executed ":lib:classes", ":lib:utilClasses", ":lib:utilDir", ":lib:utilJar", ":ui:classes", ":app:resolve"
    }

    def "can create a view for configuration that has no attributes"() {
        given:
        buildFile << """
            project(':lib') {
                configurations {
                    compile {
                        outgoing {
                            variants {
                                jarFormat {
                                    artifact file: file('lib.jar'), builtBy: tasks.jar
                                }
                                classesFormat {
                                    artifact file: file('lib.classes'), builtBy: tasks.classes
                                }
                                dirFormat {
                                    artifact file: file('lib'), builtBy: tasks.dir
                                }
                            }
                        }
                    }
                }
            }

            project(':app') {
                configurations {
                    noAttributes
                }

                dependencies {
                    noAttributes project(path: ':lib', configuration: 'compile')
                }

                task resolve {
                    def files = configurations.noAttributes.incoming.artifactView {
                        attributes { it.attribute(artifactType, 'classes') }
                    }.files
                    inputs.files files
                    doLast {
                        assert files.collect { it.name } == ['lib.classes']
                    }
                }
            }
        """

        expect:
        succeeds "resolve"
        result.assertTasksExecuted(":lib:classes", ":app:resolve")
    }

    def "fails when multiple variants match"() {
        given:
        buildFile << """
            project(':lib') {
                configurations {
                    compile {
                        outgoing {
                            variants {
                                debug {
                                    attributes.attribute(buildType, 'debug')
                                    artifact file: file('lib-debug.jar')
                                }
                                release {
                                    attributes.attribute(buildType, 'release')
                                    artifact file: file('lib-release.jar')
                                }
                            }
                        }
                    }
                }
                artifacts {
                    compile file('implicit.jar')
                }
            }

            project(':app') {
                dependencies {
                    compile project(':lib')
                }

                task resolveView {
                    def files = configurations.compile.incoming.artifactView {
                        attributes { it.attribute(artifactType, 'jar') }
                    }.files
                    inputs.files files
                    doLast {
                        files.collect { it.name }
                    }
                }
            }
        """

        expect:
        fails "resolveView"
        failure.assertHasDescription("Could not determine the dependencies of task ':app:resolveView'.")
        failure.assertHasCause("""More than one variant matches the consumer attributes:
  - Variant:
      - Required artifactType 'jar' and found compatible value 'jar'.
      - Required usage 'api' and found compatible value 'api'.
  - Variant:
      - Required artifactType 'jar' and found compatible value 'jar'.
      - Found buildType 'debug' but wasn't required.
      - Required usage 'api' and found compatible value 'api'.
  - Variant:
      - Required artifactType 'jar' and found compatible value 'jar'.
      - Found buildType 'release' but wasn't required.
      - Required usage 'api' and found compatible value 'api'.""")
    }

    def "returns empty result when no variants match and view attributes specified"() {
        given:
        buildFile << """
            project(':lib') {
                configurations {
                    compile {
                        outgoing {
                            variants {
                                debug {
                                    attributes.attribute(buildType, 'debug')
                                    artifact file: file('lib-debug.jar')
                                }
                                release {
                                    attributes.attribute(buildType, 'release')
                                    artifact file: file('lib-release.jar')
                                }
                            }
                        }
                    }
                }
                artifacts {
                    compile file('implicit.jar')
                }
            }

            project(':app') {
                dependencies {
                    compile project(':lib')
                }

                task resolveView {
                    def files = configurations.compile.incoming.artifactView {
                        attributes { it.attribute(artifactType, 'dll') }
                    }.files
                    inputs.files files
                    doLast {
                        assert files.empty
                    }
                }
            }
        """

        expect:
        succeeds "resolveView"
        result.assertTasksExecuted(":app:resolveView")
    }

    def "fails when no variants match and no view attributes specified"() {
        given:
        buildFile << """
            project(':lib') {
                configurations {
                    compile {
                        outgoing {
                            variants {
                                debug {
                                    attributes.attribute(buildType, 'debug')
                                    artifact file: file('lib-debug.jar')
                                }
                                release {
                                    attributes.attribute(buildType, 'release')
                                    artifact file: file('lib-release.jar')
                                }
                            }
                        }
                    }
                }
                artifacts {
                    compile file('implicit.jar')
                }
            }

            project(':app') {
                configurations.compile.attributes.attribute(artifactType, 'dll')
                
                dependencies {
                    compile project(':lib')
                }

                task resolveView {
                    def files = configurations.compile.incoming.artifactView { }.files
                    inputs.files files
                    doLast {
                        assert files.empty
                    }
                }
            }
        """

        expect:
        fails "resolveView"
        failure.assertHasCause("""No variants match the consumer attributes:
  - Variant:
      - Required artifactType 'dll' and found incompatible value 'jar'.
      - Required usage 'api' and found compatible value 'api'.
  - Variant:
      - Required artifactType 'dll' and found incompatible value 'jar'.
      - Found buildType 'debug' but wasn't required.
      - Required usage 'api' and found compatible value 'api'.
  - Variant:
      - Required artifactType 'dll' and found incompatible value 'jar'.
      - Found buildType 'release' but wasn't required.
      - Required usage 'api' and found compatible value 'api'.""")
    }
}
