/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.instantexecution

class InstantExecutionArtifactTransformsIntegrationTest extends AbstractInstantExecutionIntegrationTest {

    def 'transform action is re-executed when input artifact changes'() {
        given:
        buildKotlinFile '''

abstract class Summarize : TransformAction<TransformParameters.None> {

    @get:InputArtifact
    abstract val inputArtifact: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val inputFile = inputArtifact.get().asFile
        println("Transforming ${inputFile.name}...")
        outputs.file("${inputFile.nameWithoutExtension}-summary.txt").run {
            writeText("${inputFile.name}: ${inputFile.length()}")
        }
    }
}

val summarized = Attribute.of("summarized", Boolean::class.javaObjectType)
dependencies {
    attributesSchema {
        attribute(summarized)
    }
    artifactTypes.create("txt") {
        attributes.attribute(summarized, false)
    }
    registerTransform(Summarize::class) {
        from.attribute(summarized, false)
        to.attribute(summarized, true)
    }
}

val sourceFiles by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = false
}

val summarizedFiles by configurations.creating {
    extendsFrom(sourceFiles)
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes {
        attribute(summarized, true)
    }
}

abstract class CombineSummaries : DefaultTask() {

    @get:InputFiles
    abstract val inputSummaries: ConfigurableFileCollection

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun summarize() {
        outputFile.get().asFile.run {
            parentFile.mkdirs()
            writeText(summaryString())
        }
    }

    private
    fun summaryString() = inputSummaries.files.joinToString(separator = "\\n") { it.readText() }
}

tasks.register<CombineSummaries>("summarize") {
    inputSummaries.from(summarizedFiles)
    outputFile.set(layout.buildDirectory.file("summary.txt"))
}

dependencies {
    sourceFiles(files("input.txt"))
}
'''
        def inputFile = file('input.txt').tap { write("the input file") }
        def outputFile = file('build/summary.txt')
        def expectedOutput = "input.txt: ${inputFile.length()}"
        def instant = newInstantExecutionFixture()

        when:
        instantRun 'summarize'

        then:
        instant.assertStateStored()
        outputFile.text == expectedOutput
        outputContains 'Transforming input.txt...'
        result.assertTaskExecuted ':summarize'

        when: 'input file changes'
        inputFile.text = inputFile.text.reverse()
        instantRun 'summarize'

        then:
        instant.assertStateLoaded()
        outputFile.text == expectedOutput
        outputContains 'Transforming input.txt...'
        result.assertTaskExecuted ':summarize'
    }
}
