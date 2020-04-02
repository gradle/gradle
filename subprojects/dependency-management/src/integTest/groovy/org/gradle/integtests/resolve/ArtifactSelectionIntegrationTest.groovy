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
import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
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
           attribute(usage)
           attribute(buildType)
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
                        attribute(otherAttributeOptional)
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
                        assert defaultView.files.collect { it.name } == ['lib.jar', 'lib-util.jar', 'ui.jar', 'some-jar-1.0.jar']
                        assert defaultView.artifacts.collect { it.id.displayName }  == ['lib.jar (project :lib)', 'lib-util.jar', 'ui.jar (project :ui)', 'some-jar-1.0.jar (org:test:1.0)']

                        // Get a view with additional optional attribute
                        def optionalAttributeView =  configurations.compile.incoming.artifactView {
                            attributes { 
                                it.attribute(artifactType, 'jar')
                                it.attribute(otherAttributeOptional, 'anything') 
                            }
                        }
                        assert optionalAttributeView.files.collect { it.name } == ['lib.jar', 'lib-util.jar', 'ui.jar', 'some-jar-1.0.jar']
                        assert optionalAttributeView.artifacts.collect { it.id.displayName }  == ['lib.jar (project :lib)', 'lib-util.jar', 'ui.jar (project :ui)', 'some-jar-1.0.jar (org:test:1.0)']
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

    @ToBeFixedForInstantExecution
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
                        assert view.files.collect { it.name } == ['lib.classes', 'lib-util.classes', 'ui.classes', 'some-classes-1.0.classes']
                        assert view.artifacts.collect { it.id.displayName } == ['lib.classes (project :lib)', 'lib-util.classes', 'ui.classes (project :ui)', 'some-classes-1.0.classes (org:test2:1.0)']
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
                        attributes.attribute(buildType, 'profile')
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

    @ToBeFixedForInstantExecution
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

    @ToBeFixedForInstantExecution
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
import org.gradle.api.artifacts.transform.TransformParameters

abstract class VariantArtifactTransform implements TransformAction<TransformParameters.None> {
    @InputArtifact
    abstract Provider<FileSystemLocation> getInputArtifact()

    void transform(TransformOutputs outputs) {
        def output = outputs.file("transformed-" + inputArtifact.get().asFile.name)
        output << "transformed"
    }
}

dependencies {
    compile files('test-lib.jar')
    compile project(':lib')
    compile project(':ui')
    compile 'org:test:1.0'
    registerTransform(VariantArtifactTransform) {
        from.attribute(usage, "api")
        to.attribute(usage, "transformed")
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
        attributes {
            attribute(usage, 'transformed')
        }
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
        outputContains("variants: [{artifactType=jar}, {artifactType=jar, buildType=debug, flavor=one, usage=transformed}, {artifactType=jar, usage=transformed}, {artifactType=jar, org.gradle.status=integration}]")
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
                        assert files.collect { it.name } == ['lib.classes', 'lib-util.classes', 'ui.classes', 'some-classes-1.0.classes']
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
                        attributes.attribute(buildType, 'n/a')
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
        failure.assertHasCause("Could not resolve all task dependencies for configuration ':app:compile'.")
        failure.assertHasCause("""More than one variant of project :lib matches the consumer attributes:
  - Configuration ':lib:compile':
      - Unmatched attribute:
          - Found buildType 'n/a' but wasn't required.
      - Compatible attributes:
          - Required artifactType 'jar' and found compatible value 'jar'.
          - Required usage 'api' and found compatible value 'api'.
  - Configuration ':lib:compile' variant debug:
      - Unmatched attribute:
          - Found buildType 'debug' but wasn't required.
      - Compatible attributes:
          - Required artifactType 'jar' and found compatible value 'jar'.
          - Required usage 'api' and found compatible value 'api'.
  - Configuration ':lib:compile' variant release:
      - Unmatched attribute:
          - Found buildType 'release' but wasn't required.
      - Compatible attributes:
          - Required artifactType 'jar' and found compatible value 'jar'.
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

    @ToBeFixedForInstantExecution(because = "broken file collection")
    def "fails when no variants match and no view attributes specified"() {
        ivyHttpRepo.module("test","test", "1.2").publish().allowAll()

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
                    compile 'test:test:1.2'
                    compile files('thing.jar')
                }

                task resolveView {
                    def files = configurations.compile.incoming.artifactView { }.files
                    doLast {
                        assert files.empty
                    }
                }
            }
        """

        expect:
        fails "resolveView"

        failure.assertHasDescription("Execution failed for task ':app:resolveView'.")
        failure.assertHasCause("Could not resolve all files for configuration ':app:compile'.")

        failure.assertHasCause("""No variants of project :lib match the consumer attributes:
  - Configuration ':lib:compile':
      - Incompatible attribute:
          - Required artifactType 'dll' and found incompatible value 'jar'.
      - Other attribute:
          - Required usage 'api' and found compatible value 'api'.
  - Configuration ':lib:compile' variant debug:
      - Incompatible attribute:
          - Required artifactType 'dll' and found incompatible value 'jar'.
      - Other attributes:
          - Found buildType 'debug' but wasn't required.
          - Required usage 'api' and found compatible value 'api'.
  - Configuration ':lib:compile' variant release:
      - Incompatible attribute:
          - Required artifactType 'dll' and found incompatible value 'jar'.
      - Other attributes:
          - Found buildType 'release' but wasn't required.
          - Required usage 'api' and found compatible value 'api'.""")

        failure.assertHasCause("""No variants of test:test:1.2 match the consumer attributes:
  - test:test:1.2 configuration default:
      - Incompatible attribute:
          - Required artifactType 'dll' and found incompatible value 'jar'.
      - Other attributes:
          - Found org.gradle.status 'integration' but wasn't required.
          - Required usage 'api' but no value provided.""")

        failure.assertHasCause("""No variants of thing.jar match the consumer attributes:
  - thing.jar:
      - Incompatible attribute:
          - Required artifactType 'dll' and found incompatible value 'jar'.
      - Other attribute: Required usage 'api' but no value provided.""")

    }

    def "reports failure to match attributes during selection"() {
        buildFile << """
            project(':lib') {
                def attr = Attribute.of('attr', Boolean)
                dependencies {
                    attributesSchema {
                        attribute(attr)
                    }
                }
                configurations {
                    compile {
                        outgoing {
                            variants {
                                broken1 {
                                    attributes.attribute(attr, true)
                                }
                                broken2 {
                                    attributes.attribute(attr, false)
                                }
                            }
                        }
                    }
                }
            }
            
            project(':ui') {
                def attr = Attribute.of('attr', Number)
                dependencies {
                    attributesSchema {
                        attribute(attr)
                    }
                }                
                configurations {
                    compile {
                        outgoing {
                            variants {
                                broken1 {
                                    attributes.attribute(attr, 12)
                                }
                                broken2 {
                                    attributes.attribute(attr, 10)
                                }
                            }
                        }
                    }
                }
            }

            project(':app') {
                def attr = Attribute.of('attr', String)
                dependencies {
                    attributesSchema {
                        attribute(attr)
                    }

                    compile project(':lib'), project(':ui')
                }

                task resolve {
                    doLast {
                        configurations.compile.incoming.artifactView {
                            attributes { 
                                it.attribute(attr, 'jar') 
                            }
                        }.files.each { println it }
                    }
                }
            }
        """

        expect:
        fails(":app:resolve")
        failure.assertHasDescription("Execution failed for task ':app:resolve'.")
        failure.assertHasCause("Could not resolve all files for configuration ':app:compile'.")
        failure.assertHasCause("Could not select a variant of project :lib that matches the consumer attributes.")
        failure.assertHasCause("Unexpected type for attribute 'attr' provided. Expected a value of type java.lang.String but found a value of type java.lang.Boolean.")
        failure.assertHasCause("Could not select a variant of project :ui that matches the consumer attributes.")
        failure.assertHasCause("Unexpected type for attribute 'attr' provided. Expected a value of type java.lang.String but found a value of type java.lang.Integer.")
    }
}
