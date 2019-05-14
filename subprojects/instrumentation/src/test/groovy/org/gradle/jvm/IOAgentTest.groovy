/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.jvm

import net.bytebuddy.agent.ByteBuddyAgent
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths

class IOAgentTest extends Specification {
    final files = [
        fileInputStreamFile: 'FileInputStream.read.txt',
        fileOutputStream: 'FileOutputStream.write.txt',
        fileExists: 'File.exists.txt',
        filesDelete: 'Files.delete.txt',
        filesReadAllLines: 'Files.fileReader.txt',
        fileReader: 'BufferedReader.FileReader.readLine.txt',
        fileScanner: 'Scanner.readLine.txt',
        groovyFileTextRead: 'groovy.File.textRead.txt',
        groovyFileTextWrite: 'groovy.File.textWrite.txt',
        groovyPathTextRead: 'groovy.Path.textRead.txt',
        groovyPathTextWrite: 'groovy.Path.textWrite.txt',
    ]

    def output = Paths.get("build/undeclared.txt")

    def setup() {
        files.each { _, file ->
            def io = new File(file)
            io.text = "content"
            io.deleteOnExit()
        }

        IOAgent.premain(null, ByteBuddyAgent.install())
    }

    def "IOAgent"() {
        new FileInputStream(files.fileInputStreamFile).read()

        new FileOutputStream(files.fileOutputStream).write("text".getBytes())

        new File(files.fileExists).exists() // goes to file system
        new File("File.toString.txt").toString() // ignored, works with the parameter only

        Files.delete(Paths.get(files.filesDelete))
        Files.readAllLines(Paths.get(files.filesReadAllLines), StandardCharsets.UTF_8)

        new BufferedReader(new FileReader(files.fileReader)).readLine()

        new Scanner(new File(files.fileScanner)).next()

        new File(files.groovyFileTextRead).text
        new File(files.groovyFileTextWrite).text = "change"

        Paths.get(files.groovyPathTextRead).text
        Paths.get(files.groovyPathTextWrite).text = "change"

        expect:
        def undeclaredFiles = getUndeclared()
        println undeclaredFiles.join("\n") // TODO remove, when stable
        [] != undeclaredFiles
        undeclaredFiles.toSet().size() == undeclaredFiles.size()
        [] == undeclaredFiles.findAll { undeclared -> !files.any { _, file -> undeclared.contains(file) } }
    }

    // TODO this logic should partially be in the agent itself
    def getUndeclared() {
        output.getText("UTF-16").replace('\0', '') // TODO read channel properly
            .lines()
            .collect { Paths.get(it).toAbsolutePath().toString() } // map to absolute path
            .findAll { !it.matches(".+/(?:out|build)/.+") } // exclude known inputs/outputs // TODO should be done in the Agent instead
    }

}
