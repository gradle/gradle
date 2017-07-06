/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.scala.compile

import org.gradle.test.fixtures.file.TestFile

class ScalaIncrementalCompilationFixture {
    private final TestFile root
    final ScalaClass basicClassSource
    final ScalaClass classDependingOnBasicClassSource
    final ScalaClass independentClassSource
    final String sourceSet

    ScalaIncrementalCompilationFixture(File root) {
        this.root = new TestFile(root)
        this.sourceSet = 'main'
        basicClassSource = new ScalaClass(
            'Person',
            'class Person(val name: String, val age: Int)',
            'class Person(val name: String, val age: Int, val height: Int)')
        classDependingOnBasicClassSource = new ScalaClass(
            'House',
            'class House(val owner: Person)',
            'class House(val owner: Person, val residents: List[Person])'
        )
        independentClassSource = new ScalaClass(
            'Other',
            'class Other',
            'class Other(val some: String)'
        )
    }

    void baseline() {
        basicClassSource.create()
        classDependingOnBasicClassSource.create()
        independentClassSource.create()
    }

    List<ScalaClass> getAll() {
        return [basicClassSource, classDependingOnBasicClassSource, independentClassSource]
    }

    List<Long> getAllClassesLastModified() {
        return all*.compiledClass*.lastModified()
    }

    class ScalaClass {
        final TestFile source
        final TestFile compiledClass
        final String originalText
        final String changedText

        ScalaClass(String path, String originalText, String changedText) {
            this.changedText = changedText
            this.originalText = originalText
            source = root.file("src/${sourceSet}/scala/${path}.scala")
            compiledClass = root.file("build/classes/scala/main/${path}.class")
        }

        void create() {
            source.text = originalText
        }

        void change() {
            source.text = changedText
        }
    }

}
