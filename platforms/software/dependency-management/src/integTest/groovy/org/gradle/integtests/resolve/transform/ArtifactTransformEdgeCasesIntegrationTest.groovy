/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.integtests.resolve.transform


import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.ExpectedDeprecationWarning
import org.gradle.util.GradleVersion
import org.gradle.util.internal.ToBeImplemented

/**
 * This class tests interesting edge case scenarios involving registering Artifact Transforms.
 * <p>
 * These tests describe <strong>current</strong> behavior, but not necessarily <strong>desired</strong> behavior.
 */
class ArtifactTransformEdgeCasesIntegrationTest extends AbstractIntegrationSpec implements ArtifactTransformTestFixture {
    def "multiple distinct transformation chains fails with a reasonable message"() {
        file("my-initial-file.txt") << "Contents"

        buildKotlinFile << """
            val color = Attribute.of("color", String::class.java)
            val shape = Attribute.of("shape", String::class.java)
            val matter = Attribute.of("matter", String::class.java)
            val texture = Attribute.of("texture", String::class.java)

            configurations {
                // Supply a square-blue-liquid variant
                consumable("squareBlueLiquidElements") {
                    attributes.attribute(shape, "square")
                    attributes.attribute(color, "blue")
                    attributes.attribute(matter, "liquid")
                    attributes.attribute(texture, "unknown")

                    outgoing {
                        artifact(file("my-initial-file.txt"))
                    }
                }

                dependencyScope("myDependencies")

                // Initial ask is for liquid, satisfied by the square-blue-liquid variant
                resolvable("resolveMe") {
                    extendsFrom(configurations.getByName("myDependencies"))
                    attributes.attribute(matter, "liquid")
                }
            }

            abstract class BrokenTransform : TransformAction<TransformParameters.None> {
                override fun transform(outputs: TransformOutputs) {
                    throw AssertionError("Should not actually be selected to run")
                }
            }

            dependencies {
                add("myDependencies", project(":"))

                // blue -> purple -> red
                registerTransform(BrokenTransform::class.java) {
                    from.attribute(color, "blue")
                    from.attribute(texture, "unknown")
                    to.attribute(color, "purple")
                    to.attribute(texture, "rough")
                }
                registerTransform(BrokenTransform::class.java) {
                    from.attribute(color, "purple")
                    to.attribute(color, "red")
                }

                // square -> triangle -> round
                registerTransform(BrokenTransform::class.java) {
                    from.attribute(shape, "square")
                    to.attribute(shape, "triangle")
                }
                registerTransform(BrokenTransform::class.java) {
                    from.attribute(shape, "triangle")
                    to.attribute(shape, "round")
                }

                // blue -> yellow -> red
                registerTransform(BrokenTransform::class.java) {
                    from.attribute(color, "blue")
                    from.attribute(texture, "unknown")
                    to.attribute(color, "yellow")
                    to.attribute(texture, "smooth")
                }
                registerTransform(BrokenTransform::class.java) {
                    from.attribute(color, "yellow")
                    to.attribute(color, "red")
                }

                // square -> flat -> round
                registerTransform(BrokenTransform::class.java) {
                    from.attribute(shape, "square")
                    to.attribute(shape, "flat")
                }
                registerTransform(BrokenTransform::class.java) {
                    from.attribute(shape, "flat")
                    to.attribute(shape, "round")
                }
            }

            val forceResolution by tasks.registering {
                inputs.files(configurations.getByName("resolveMe").incoming.artifactView {
                    // After getting initial square-blue-liquid variant with liquid request, we request something red-round
                    // There should be 2 separate transformation chains of equal length that produce this
                    attributes.attribute(color, "red")
                    attributes.attribute(shape, "round")
                }.artifacts.artifactFiles)

                doLast {
                    inputs.files.files.forEach { println(it) }
                }
            }
        """

        expect:
        fails "forceResolution"

        failure.assertHasDescription("Could not determine the dependencies of task ':forceResolution'.")
        failure.assertHasCause("Could not resolve all dependencies for configuration ':resolveMe'.")
        failure.assertHasErrorOutput("""   > Found multiple transformation chains that produce a variant of 'root project :' with requested attributes:
       - color 'red'
       - matter 'liquid'
       - shape 'round'
     Found the following transformation chains:
       - From configuration ':squareBlueLiquidElements':
           - With source attributes:
               - artifactType 'txt'
               - color 'blue'
               - matter 'liquid'
               - shape 'square'
               - texture 'unknown'
           - Candidate transformation chains:
               - Transformation chain: 'BrokenTransform' -> 'BrokenTransform' -> 'BrokenTransform' -> 'BrokenTransform':
                   - 'BrokenTransform':
                       - Converts from attributes:
                           - shape 'square'
                       - To attributes:
                           - shape 'triangle'
                   - 'BrokenTransform':
                       - Converts from attributes:
                           - shape 'triangle'
                       - To attributes:
                           - shape 'round'
                   - 'BrokenTransform':
                       - Converts from attributes:
                           - color 'blue'
                           - texture 'unknown'
                       - To attributes:
                           - color 'purple'
                           - texture 'rough'
                   - 'BrokenTransform':
                       - Converts from attributes:
                           - color 'purple'
                       - To attributes:
                           - color 'red'
               - Transformation chain: 'BrokenTransform' -> 'BrokenTransform' -> 'BrokenTransform' -> 'BrokenTransform':
                   - 'BrokenTransform':
                       - Converts from attributes:
                           - shape 'square'
                       - To attributes:
                           - shape 'flat'
                   - 'BrokenTransform':
                       - Converts from attributes:
                           - shape 'flat'
                       - To attributes:
                           - shape 'round'
                   - 'BrokenTransform':
                       - Converts from attributes:
                           - color 'blue'
                           - texture 'unknown'
                       - To attributes:
                           - color 'purple'
                           - texture 'rough'
                   - 'BrokenTransform':
                       - Converts from attributes:
                           - color 'purple'
                       - To attributes:
                           - color 'red'
               - Transformation chain: 'BrokenTransform' -> 'BrokenTransform' -> 'BrokenTransform' -> 'BrokenTransform':
                   - 'BrokenTransform':
                       - Converts from attributes:
                           - color 'blue'
                           - texture 'unknown'
                       - To attributes:
                           - color 'yellow'
                           - texture 'smooth'
                   - 'BrokenTransform':
                       - Converts from attributes:
                           - color 'yellow'
                       - To attributes:
                           - color 'red'
                   - 'BrokenTransform':
                       - Converts from attributes:
                           - shape 'square'
                       - To attributes:
                           - shape 'triangle'
                   - 'BrokenTransform':
                       - Converts from attributes:
                           - shape 'triangle'
                       - To attributes:
                           - shape 'round'
               - Transformation chain: 'BrokenTransform' -> 'BrokenTransform' -> 'BrokenTransform' -> 'BrokenTransform':
                   - 'BrokenTransform':
                       - Converts from attributes:
                           - shape 'square'
                       - To attributes:
                           - shape 'flat'
                   - 'BrokenTransform':
                       - Converts from attributes:
                           - shape 'flat'
                       - To attributes:
                           - shape 'round'
                   - 'BrokenTransform':
                       - Converts from attributes:
                           - color 'blue'
                           - texture 'unknown'
                       - To attributes:
                           - color 'yellow'
                           - texture 'smooth'
                   - 'BrokenTransform':
                       - Converts from attributes:
                           - color 'yellow'
                       - To attributes:
                           - color 'red'""")
    }

    def "multiple distinct transformation chains fails with a reasonable message with different transform types and non-corresponding from-to attribute pairs"() {
        file("my-initial-file.txt") << "Contents"

        buildKotlinFile << """
            val color = Attribute.of("color", String::class.java)
            val shape = Attribute.of("shape", String::class.java)
            val matter = Attribute.of("matter", String::class.java)
            val texture = Attribute.of("texture", String::class.java)

            configurations {
                // Supply a square-blue-liquid-smooth variant
                consumable("squareBlueLiquidSmoothElements") {
                    attributes.attribute(shape, "square")
                    attributes.attribute(color, "blue")
                    attributes.attribute(matter, "liquid")
                    attributes.attribute(texture, "smooth")

                    outgoing {
                        artifact(file("my-initial-file.txt"))
                    }
                }

                dependencyScope("myDependencies")

                // Initial ask is for liquid, satisfied by the square-blue-liquid-smooth variant
                resolvable("resolveMe") {
                    extendsFrom(configurations.getByName("myDependencies"))
                    attributes.attribute(matter, "liquid")
                }
            }

            abstract class BrokenColorTransform : TransformAction<TransformParameters.None> {
                override fun transform(outputs: TransformOutputs) {
                    throw AssertionError("Should not actually be selected to run")
                }
            }

            abstract class BrokenShapeTransform : TransformAction<TransformParameters.None> {
                override fun transform(outputs: TransformOutputs) {
                    throw AssertionError("Should not actually be selected to run")
                }
            }

            dependencies {
                add("myDependencies", project(":"))

                // blue/smooth -> red/bumpy
                registerTransform(BrokenColorTransform::class.java) {
                    from.attribute(color, "blue")
                    from.attribute(texture, "smooth")
                    to.attribute(color, "red")
                    to.attribute(texture, "bumpy")
                }

                // blue/smooth -> red/rough
                registerTransform(BrokenColorTransform::class.java) {
                    from.attribute(color, "blue")
                    from.attribute(texture, "smooth")
                    to.attribute(color, "red")
                    to.attribute(texture, "rough")
                }

                // square/rough -> round
                registerTransform(BrokenShapeTransform::class.java) {
                    from.attribute(shape, "square")
                    from.attribute(texture, "rough")
                    to.attribute(shape, "round")
                }

                // square/bumpy -> round
                registerTransform(BrokenShapeTransform::class.java) {
                    from.attribute(shape, "square")
                    from.attribute(texture, "bumpy")
                    to.attribute(shape, "round")
                }
            }

            val forceResolution by tasks.registering {
                inputs.files(configurations.getByName("resolveMe").incoming.artifactView {
                    // After getting initial square-blue-liquid-smooth variant with liquid request, we request something red-round
                    // There should be 2 separate transformation chains of equal length that produce this
                    attributes.attribute(color, "red")
                    attributes.attribute(shape, "round")
                }.artifacts.artifactFiles)

                doLast {
                    inputs.files.files.forEach { println(it) }
                }
            }
        """

        expect:
        fails "forceResolution"

        failure.assertHasDescription("Could not determine the dependencies of task ':forceResolution'.")
        failure.assertHasCause("Could not resolve all dependencies for configuration ':resolveMe'.")
        failure.assertHasErrorOutput("""   > Found multiple transformation chains that produce a variant of 'root project :' with requested attributes:
       - color 'red'
       - matter 'liquid'
       - shape 'round'
     Found the following transformation chains:
       - From configuration ':squareBlueLiquidSmoothElements':
           - With source attributes:
               - artifactType 'txt'
               - color 'blue'
               - matter 'liquid'
               - shape 'square'
               - texture 'smooth'
           - Candidate transformation chains:
               - Transformation chain: 'BrokenColorTransform' -> 'BrokenShapeTransform':
                   - 'BrokenColorTransform':
                       - Converts from attributes:
                           - color 'blue'
                           - texture 'smooth'
                       - To attributes:
                           - color 'red'
                           - texture 'bumpy'
                   - 'BrokenShapeTransform':
                       - Converts from attributes:
                           - shape 'square'
                           - texture 'bumpy'
                       - To attributes:
                           - shape 'round'
               - Transformation chain: 'BrokenColorTransform' -> 'BrokenShapeTransform':
                   - 'BrokenColorTransform':
                       - Converts from attributes:
                           - color 'blue'
                           - texture 'smooth'
                       - To attributes:
                           - color 'red'
                           - texture 'rough'
                   - 'BrokenShapeTransform':
                       - Converts from attributes:
                           - shape 'square'
                           - texture 'rough'
                       - To attributes:
                           - shape 'round'""")
    }

    def "multiple identical attribute transformations of distinct types should fail"() {
        file("my-initial-file.txt") << "Contents"

        buildKotlinFile << """
            val color = Attribute.of("color", String::class.java)
            val shape = Attribute.of("shape", String::class.java)
            val texture = Attribute.of("texture", String::class.java)

            configurations {
                // Supply a square-blue-smooth variant
                consumable("squareBlueSmoothElements") {
                    attributes.attribute(shape, "square")
                    attributes.attribute(color, "blue")
                    attributes.attribute(texture, "smooth")

                    outgoing {
                        artifact(file("my-initial-file.txt"))
                    }
                }

                dependencyScope("myDependencies")

                // Initial ask is for smooth, satisfied by the square-blue-smooth variant
                resolvable("resolveMe") {
                    extendsFrom(configurations.getByName("myDependencies"))
                    attributes.attribute(texture, "smooth")
                }
            }

            abstract class BrokenColorTransform : TransformAction<TransformParameters.None> {
                override fun transform(outputs: TransformOutputs) {
                    throw AssertionError("Should not actually be selected to run")
                }
            }

            abstract class BrokenColorTransform2 : TransformAction<TransformParameters.None> {
                override fun transform(outputs: TransformOutputs) {
                    throw AssertionError("Should not actually be selected to run")
                }
            }

            dependencies {
                add("myDependencies", project(":"))

                // blue/smooth -> red (type 1)
                registerTransform(BrokenColorTransform::class.java) {
                    from.attribute(color, "blue")
                    from.attribute(texture, "smooth")
                    to.attribute(color, "red")
                }

                // blue/smooth -> red (type 2)
                registerTransform(BrokenColorTransform2::class.java) {
                    from.attribute(color, "blue")
                    from.attribute(texture, "smooth")
                    to.attribute(color, "red")
                }
            }

            val forceResolution by tasks.registering {
                inputs.files(configurations.getByName("resolveMe").incoming.artifactView {
                    // After getting initial square-blue-smooth variant with smooth request, we request something red
                    // There should be 2 separate transformation chains of equal length that produce this
                    attributes.attribute(color, "red")
                }.artifacts.artifactFiles)

                doLast {
                    inputs.files.files.forEach { println(it) }
                }
            }
        """

        expect:
        executer.expectDeprecationWarning(ExpectedDeprecationWarning.withMessage("There are multiple distinct artifact transformation chains of the same length that would satisfy this request. This behavior has been deprecated. This will fail with an error in Gradle 9.0. " + """
Found multiple transformation chains that produce a variant of 'root project :' with requested attributes:
  - color 'red'
  - texture 'smooth'
Found the following transformation chains:
  - From configuration ':squareBlueSmoothElements':
      - With source attributes:
          - artifactType 'txt'
          - color 'blue'
          - shape 'square'
          - texture 'smooth'
      - Candidate transformation chains:
          - Transformation chain: 'BrokenColorTransform':
              - 'BrokenColorTransform':
                  - Converts from attributes:
                      - color 'blue'
                      - texture 'smooth'
                  - To attributes:
                      - color 'red'
          - Transformation chain: 'BrokenColorTransform2':
              - 'BrokenColorTransform2':
                  - Converts from attributes:
                      - color 'blue'
                      - texture 'smooth'
                  - To attributes:
                      - color 'red'
 Remove one or more registered transforms, or add additional attributes to them to ensure only a single valid transformation chain exists. Consult the upgrading guide for further information: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_8.html#deprecated_ambiguous_transformation_chains"""))

        fails "forceResolution"

        // Currently, Gradle emits a deprecation warning and this only fails because the transforms throw exceptions.
        // After Gradle 9.0, remove the deprecation expectation and this (currently passing) assertion:
        failure.assertHasCause("Should not actually be selected to run")
        // ...and uncomment the assertions below, which test what SHOULD happen after the deprecation is made an error in Gradle 9.0:
/*
        failure.assertHasDescription("Could not determine the dependencies of task ':forceResolution'.")
        failure.assertHasCause("Could not resolve all dependencies for configuration ':resolveMe'.")
        failure.assertHasErrorOutput("""   > Found multiple transformation chains that produce a variant of 'root project :' with requested attributes:
  - color 'red'
  - texture 'smooth'
Found the following transformation chains:
  - From configuration ':squareBlueSmoothElements':
      - With source attributes:
          - artifactType 'txt'
          - color 'blue'
          - shape 'square'
          - texture 'smooth'
      - Candidate transformation chains:
          - Transformation chain: 'BrokenColorTransform':
              - 'BrokenColorTransform':
                  - Converts from attributes:
                      - color 'blue'
                      - texture 'smooth'
                  - To attributes:
                      - color 'red'
          - Transformation chain: 'BrokenColorTransform2':
              - 'BrokenColorTransform2':
                  - Converts from attributes:
                      - color 'blue'
                      - texture 'smooth'
                  - To attributes:
                      - color 'red'""")
*/
    }

    def "transforms from selected chains aren't instantiated and don't run if there are no artifacts on the source variant"() {
        file("my-initial-file.txt") << "Contents"

        buildKotlinFile << """
            val color = Attribute.of("color", String::class.java)
            val shape = Attribute.of("shape", String::class.java)

            configurations {
                // Supply a square-blue variant, without artifacts
                consumable("squareBlueSmoothElements") {
                    attributes.attribute(shape, "square")
                    attributes.attribute(color, "blue")
                }

                dependencyScope("myDependencies")

                // Initial ask is for square, satisfied by the square-blue variant
                resolvable("resolveMe") {
                    extendsFrom(configurations.getByName("myDependencies"))
                    attributes.attribute(shape, "square")
                }
            }

            abstract class BrokenColorTransform : TransformAction<TransformParameters.None> {
                init {
                    throw AssertionError("Should not ever be instantiated")
                }

                override fun transform(outputs: TransformOutputs) {
                    throw AssertionError("Should not actually be selected to run")
                }
            }

            dependencies {
                add("myDependencies", project(":"))

                // blue -> red
                registerTransform(BrokenColorTransform::class.java) {
                    from.attribute(color, "blue")
                    to.attribute(color, "red")
                }
            }

            val forceResolution by tasks.registering {
                inputs.files(configurations.getByName("resolveMe").incoming.artifactView {
                    // After getting initial square-blue variant with square request, we request something also red
                    // A transformation must be run to produce this, but it shouldn't be created or run because there are no artifacts
                    attributes.attribute(color, "red")
                }.artifacts.artifactFiles)

                doLast {
                    inputs.files.files.forEach { println(it) }
                }
            }
        """

        expect:
        succeeds "forceResolution"
    }

    def "if a transform removes all artifacts from a variant, leaving an empty directory, subsequent transforms in selected chain still run"() {
        file("my-initial-file.txt") << "Contents"

        buildKotlinFile << """
            val color = Attribute.of("color", String::class.java)
            val shape = Attribute.of("shape", String::class.java)
            val matter = Attribute.of("matter", String::class.java)

            configurations {
                // Supply a square-blue-liquid variant, with an artifact
                consumable("squareBlueSmoothElements") {
                    attributes.attribute(shape, "square")
                    attributes.attribute(color, "blue")
                    attributes.attribute(matter, "liquid")

                    outgoing {
                        artifact(file("my-initial-file.txt"))
                    }
                }

                dependencyScope("myDependencies")

                // Initial ask is for liquid, satisfied by the square-blue-liquid variant
                resolvable("resolveMe") {
                    extendsFrom(configurations.getByName("myDependencies"))
                    attributes.attribute(matter, "liquid")
                }
            }

            abstract class ShapeTransform : TransformAction<TransformParameters.None> {
                @get:InputArtifact
                abstract val inputArtifact: Provider<FileSystemLocation>

                override fun transform(outputs: TransformOutputs) {
                    outputs.dir("empty")
                }
            }

            abstract class ColorTransform : TransformAction<TransformParameters.None> {
                @get:InputArtifact
                abstract val inputArtifact: Provider<FileSystemLocation>

                override fun transform(outputs: TransformOutputs) {
                    assert(inputArtifact.get().getAsFile().getName() == "empty")
                }
            }

            dependencies {
                add("myDependencies", project(":"))

                // square -> round
                registerTransform(ShapeTransform::class.java) {
                    from.attribute(shape, "square")
                    to.attribute(shape, "round")
                }

                // blue/round -> red (must run second)
                registerTransform(ColorTransform::class.java) {
                    from.attribute(color, "blue")
                    from.attribute(shape, "round")
                    to.attribute(color, "red")
                }
            }

            val forceResolution by tasks.registering {
                inputs.files(configurations.getByName("resolveMe").incoming.artifactView {
                    // After getting initial square-blue variant with square request, we request something red-round
                    // A transformation chain must be run to produce this, but the first transformation should remove input artifacts, resulting in the color transform running on empty dir
                    attributes.attribute(color, "red")
                    attributes.attribute(shape, "round")
                }.artifacts.artifactFiles)

                doLast {
                    inputs.files.files.forEach { println(it) }
                }
            }
        """

        expect:
        succeeds "forceResolution"
    }

    @ToBeImplemented("https://github.com/gradle/gradle/issues/30784")
    def "registering multiple transformations using the same type and from and to attributes should fail"() {
        file("my-initial-file.txt") << "Contents"

        buildKotlinFile << """
            val color = Attribute.of("color", String::class.java)
            val shape = Attribute.of("shape", String::class.java)

            configurations {
                // Supply a square-blue variant
                consumable("squareBlueLiquidElements") {
                    attributes.attribute(shape, "square")
                    attributes.attribute(color, "blue")

                    outgoing {
                        artifact(file("my-initial-file.txt"))
                    }
                }

                dependencyScope("myDependencies")

                // Initial ask is for square, satisfied by the square-blue variant
                resolvable("resolveMe") {
                    extendsFrom(configurations.getByName("myDependencies"))
                    attributes.attribute(shape, "square")
                }
            }

            abstract class BrokenTransform : TransformAction<TransformParameters.None> {
                override fun transform(outputs: TransformOutputs) {
                    throw AssertionError("Should not actually be selected to run")
                }
            }

            dependencies {
                add("myDependencies", project(":"))

                // blue -> red
                registerTransform(BrokenTransform::class.java) {
                    from.attribute(color, "blue")
                    to.attribute(color, "red")
                }

                // blue -> red (identical transform)
                registerTransform(BrokenTransform::class.java) {
                    from.attribute(color, "blue")
                    to.attribute(color, "red")
                }
            }

            val forceResolution by tasks.registering {
                inputs.files(configurations.getByName("resolveMe").incoming.artifactView {
                    // After getting initial square-blue variant with blue request, we request something red
                    attributes.attribute(shape, "red")
                }.artifacts.artifactFiles)

                doLast {
                    inputs.files.files.forEach { println(it) }
                }
            }
        """

        expect:
        // TODO: This should FAIL with an error about registering multiple duplicate transforms
        succeeds "tasks"
    }

    // region Demo Resolving Ambiguity
    // These tests expand a test in DisambiguateArtifactTransformIntegrationTest, and explore  how to resolve the situation
    def "when A -> C and B -> C both produce identical attributes, the later is currently by selected, unless an additional distinct attribute is added to each result to remove the latent ambiguity"() {
        given:
        setupDisambiguationTest()

        when: "without any distinguishing attributes, we have latent ambiguity, which is reported, and an arbitrary selection is made"
        executer.expectDeprecationWarning("There are multiple distinct artifact transformation chains of the same length that would satisfy this request. This behavior has been deprecated. This will fail with an error in Gradle 9.0. ")
        succeeds "resolve"

        then:
        output.count("Transforming") == 2
        output.count("Transforming lib.jar to lib.jar.txt") == 1
        output.count("Transforming test-1.3.jar to test-1.3.jar.txt") == 1

        when: "if an additional attribute is present on both chains, then we produce distinct, non-mutually compatible variants, and fail with ambiguity"
        fails 'resolve', '-PextraAttributeA', '-PextraAttributeB'

        then:
        failureCauseContains('Found multiple transformation chains')
    }

    def "when A -> C and B -> C both produce identical attributes, adding an additional attribute to either removes the latent ambiguity and causes the other to be selected as the better match"() {
        given:
        setupDisambiguationTest()

        when: "if only transform A produces an extra attribute, then it produces a final variant that is farther from the request because of it, so B is the winner"
        succeeds 'resolve', '-PextraAttributeA'

        then:
        output.count("Transforming") == 2
        output.count("Transforming lib.jar to lib.jar.txt") == 1
        output.count("Transforming test-1.3.jar to test-1.3.jar.txt") == 1

        when: "and vice-versa with B"
        succeeds 'resolve', '-PextraAttributeB'

        then:
        output.count("Transforming") == 1
        output.count("Transforming main to main.txt") == 1
    }

    def "when A -> C and B -> C differ by a single discriminating attribute, adding that additional attribute resolves the ambiguity"() {
        given:
        setupDisambiguationTest()

        when:
        succeeds 'resolve', '-PextraAttributeA', '-PextraAttributeB', '-PextraRequest=value2'

        then:
        output.count("Transforming") == 2
        output.count("Transforming lib.jar to lib.jar.txt") == 1
        output.count("Transforming test-1.3.jar to test-1.3.jar.txt") == 1
    }

    private void setupDisambiguationTest() {
        def m1 = mavenRepo.module("test", "test", "1.3").publish()
        m1.artifactFile.text = "1234"

        createDirs("lib", "app")
        settingsFile << """
            rootProject.name = 'root'
            include 'lib'
            include 'app'
        """

        file('lib/src/main/java/test/MyClass.java') << """
package test;

public class MyClass {
    public static void main(String[] args) {
        System.out.println("Hello world!");
    }
}
"""

        buildFile << """
def artifactType = Attribute.of('artifactType', String)
def extraAttribute = Attribute.of('extra', String)

allprojects {
    repositories {
        maven { url "${mavenRepo.uri}" }
    }
}
project(':lib') {
    apply plugin: 'java-library'
}

project(':app') {
    apply plugin: 'java'

    dependencies {
        implementation 'test:test:1.3'
        implementation project(':lib')
    }

    def hasExtraAttributeA = providers.gradleProperty('extraAttributeA').isPresent()
    def hasExtraAttributeB = providers.gradleProperty('extraAttributeB').isPresent()
    def extraRequest = providers.gradleProperty('extraRequest')

    dependencies {
        registerTransform(TestTransform) { // A
            from.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_API))
            from.attribute(artifactType, 'java-classes-directory')
            to.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_API))
            to.attribute(artifactType, 'final')

            if (hasExtraAttributeA) {
                from.attribute(extraAttribute, 'whatever')
                to.attribute(extraAttribute, 'value1')
            }
        }
        registerTransform(TestTransform) { // B
            from.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_API))
            from.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements, LibraryElements.JAR))
            from.attribute(artifactType, 'jar')
            to.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_API))
            to.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements, LibraryElements.CLASSES))
            to.attribute(artifactType, 'final')

            if (hasExtraAttributeB) {
                from.attribute(extraAttribute, 'whatever')
                to.attribute(extraAttribute, 'value2')
            }
        }
    }

    task resolve(type: Copy) {
        def artifacts = configurations.compileClasspath.incoming.artifactView {
            attributes {
                attribute(artifactType, 'final')
                if (extraRequest.isPresent()) {
                    attribute(extraAttribute, extraRequest.get())
                }
            }
        }.artifacts
        from artifacts.artifactFiles
        into "\${buildDir}/libs"
        doLast {
            println "files: " + artifacts.collect { it.file.name }
            println "ids: " + artifacts.collect { it.id }
            println "components: " + artifacts.collect { it.id.componentIdentifier }
            println "variants: " + artifacts.collect { it.variant.attributes }
        }
    }
}

${artifactTransform("TestTransform")}
"""
    }

    private String artifactTransform(String className, String extension = "txt", String message = "Transforming") {
        """
            import org.gradle.api.artifacts.transform.TransformParameters

            abstract class ${className} implements TransformAction<TransformParameters.None> {
                ${className}() {
                    println "Creating ${className}"
                }

                @InputArtifact
                abstract Provider<FileSystemLocation> getInputArtifact()

                void transform(TransformOutputs outputs) {
                    def input = inputArtifact.get().asFile
                    def output = outputs.file("\${input.name}.${extension}")
                    println "${message} \${input.name} to \${output.name}"
                    output.text = String.valueOf(input.length())
                }
            }
        """
    }
    // endregion Demo Resolving Ambiguity
}
