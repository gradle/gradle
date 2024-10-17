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

import org.gradle.test.fixtures.AbstractProjectBuilderSpec

import java.nio.file.Files

abstract class SigningProjectSpec extends AbstractProjectBuilderSpec {

    static final DEFAULT_KEY_SET = "gradle"

    private assertProject() {
        assert project != null : "You haven't created a project"
    }

    protected def getSigning() {
        assertProject()
        return project.extensions.getByType(SigningExtension)
    }

    protected def signing(Closure action) {
        return project.signing(action)
    }

    protected def getConfigurations() {
        return project.configurations
    }

    protected def getJar() {
        return project.jar
    }

    def applyPlugin() {
        project.apply plugin: "signing"
    }

    def addProperties(Map props) {
        props.each { k, v ->
            project.ext.set(k, v)
        }
    }

    def addSigningProperties(keyId, secretKeyRingFile, password) {
        addPrefixedSigningProperties(null, keyId, secretKeyRingFile, password)
    }

    def addPrefixedSigningProperties(prefix, keyId, secretKeyRingFile, password) {
        def truePrefix = prefix ? "${prefix}." : ""
        def properties = [:]
        def values = [keyId: keyId, secretKeyRingFile: secretKeyRingFile, password: password]
        values.each { k, v ->
            properties["signing.${truePrefix}${k}"] = v
        }
        addProperties(properties)
        values
    }

    def getSigningPropertiesSet(setName = DEFAULT_KEY_SET) {
        def properties = [:]
        properties.keyId = getKeyResourceFile(setName, "keyId.txt").text.trim()
        properties.secretKeyRingFile = getKeyResourceFile(setName, "secring.gpg").absolutePath
        properties.password = getKeyResourceFile(setName, "password.txt").text.trim()
        properties
    }

    def addSigningProperties(Map args = [:]) {
        def properties = getSigningPropertiesSet(args.set ?: DEFAULT_KEY_SET)
        addPrefixedSigningProperties(args.prefix, properties.keyId, properties.secretKeyRingFile, properties.password)
    }

    def getKeyResourceFile(setName, fileName) {
        getResourceFile("keys/$setName/$fileName")
    }

    def getResourceFile(path) {
        def copiedFile = temporaryFolder.file(path)
        if (!copiedFile.exists()) {
            copiedFile.parentFile.mkdirs()
            Files.copy(
                getClass().classLoader.getResourceAsStream(path),
                copiedFile.toPath()
            )
        }

        copiedFile
    }

    def useJavadocAndSourceJars() {
        project.apply plugin: "java"
        project.java {
            withJavadocJar()
            withSourcesJar()
        }
    }
}
