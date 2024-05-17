/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.plugins.signing

class SigningTasksSpec extends SigningProjectSpec {

    def setup() {
        applyPlugin()
    }

    def "sign jar with defaults"() {
        given:
        useJavadocAndSourceJars()
        createJarTaskOutputFile('jar', 'sourcesJar', 'javadocJar')

        when:
        signing {
            sign jar
            sign sourcesJar, javadocJar
        }

        then:
        def signingTasks = [signJar, signSourcesJar, signJavadocJar]

        and:
        jar in signJar.dependsOn
        sourcesJar in signSourcesJar.dependsOn
        javadocJar in signJavadocJar.dependsOn

        and:
        signingTasks.every { it.singleSignature in configurations.signatures.artifacts }

        and:
        signingTasks.every { it.signatory == signing.signatory }
    }

    def "sign method return values"() {
        given:
        useJavadocAndSourceJars()

        when:
        def signJarTask = signing.sign(jar).first()

        then:
        signJarTask.name == "signJar"

        when:
        def (signSourcesJarTask, signJavadocJarTask) = signing.sign(sourcesJar, javadocJar)

        then:
        [signSourcesJarTask, signJavadocJarTask]*.name == ["signSourcesJar", "signJavadocJar"]
    }

    def "output files contain signature files for existing files"() {
        given:
        useJavadocAndSourceJars()
        applyPlugin()
        addSigningProperties()
        createJarTaskOutputFile('jar')

        when:
        Sign signTask = signing.sign(jar).first()

        then:
        def outputPath = jar.outputs.files.singleFile.toPath().toAbsolutePath().toString()
        signTask.signaturesByKey.containsKey(outputPath)
        signTask.signaturesByKey.get(outputPath) == signTask.singleSignature
    }

    def "files to sign that do not exist are ignored"() {
        given:
        useJavadocAndSourceJars()
        applyPlugin()
        addSigningProperties()

        when:
        Sign signTask = signing.sign(jar).first()
        jar.enabled = false

        then:
        signTask.signaturesByKey == [:]
    }

    def "files to sign are de-duplicated"() {
        given:
        useJavadocAndSourceJars()
        applyPlugin()
        addSigningProperties()
        createJarTaskOutputFile('jar')

        when:
        Sign signTask = signing.sign(jar).first()
        signTask.sign('', jar.outputs.files.singleFile) // add jar task output again, this time directly as File

        then:
        signTask.signatures.size() == 2
        noExceptionThrown()
        signTask.signaturesByKey.size() == 1
        def outputPath = jar.outputs.files.singleFile.toPath().toAbsolutePath().toString()
        signTask.signaturesByKey.containsKey(outputPath)
        signTask.signaturesByKey.get(outputPath) == signTask.singleSignature
    }

    def "sign task has description"() {
        given:
        useJavadocAndSourceJars()

        when:
        signing {
            sign jar, sourcesJar
        }

        then:
        signJar.description == "Signs the archive produced by the 'jar' task."
        signSourcesJar.description == "Signs the archive produced by the 'sourcesJar' task."
    }

    private createJarTaskOutputFile(String... tasksToSimulate) {
        for (def task : tasksToSimulate) {
            def jarFile = tasks.getByName(task).outputs.files.singleFile
            File libsDir = jarFile.parentFile
            libsDir.mkdirs()
            jarFile.createNewFile()
        }

    }
}
