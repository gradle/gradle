/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.integtests.fixtures.executer

import spock.lang.Specification

import java.util.zip.ZipFile

class ClassPathMergerTest extends Specification {
    def 'can merge long classpath list into one jar'() {
        when:
        List<File> classPath = generateLongClassPath()
        List<File> mergedClassPath = ClassPathMerger.INSTANCE.mergeClassPathIfNecessary(classPath)
        ZipFile zipFile = new ZipFile(mergedClassPath[0])
        String manifest = zipFile.entries().findAll { !it.directory }.collect { zipFile.getInputStream(it).text }.first()

        then:
        mergedClassPath.size() == 1
        manifest.length() > ClassPathMerger.INSTANCE.CLASSPATH_LENGTH_LIMITATION
        manifest.startsWith('Class-Path:')
        (manifest - 'Class-Path: ').replaceAll(/\r\n /, '').split(' ').every { it.startsWith('file:') }
    }

    List<File> generateLongClassPath() {
        int totalLength = 0
        String userDir = System.getProperty('user.dir')
        int fileCounter = 0
        List<File> files = []

        while (totalLength < ClassPathMerger.INSTANCE.CLASSPATH_LENGTH_LIMITATION) {
            File file = new File(userDir, fileCounter.toString())
            files.add(file)

            fileCounter += 1
            totalLength += (file.absolutePath.length() + 1)
        }
        return files
    }

    def "do nothing if classpath doesn't hti the limitation"() {
        when:
        File userDir = new File(System.getProperty('user.dir'))
        List<File> shortClassPath = [new File(userDir, 'foo'), new File(userDir, 'bar')]

        then:
        ClassPathMerger.INSTANCE.mergeClassPathIfNecessary(shortClassPath) == shortClassPath
    }
}
