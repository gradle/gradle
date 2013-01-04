/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.test.fixtures.file

/**
 * Used in TestFile.create().
 *
 * Should be inner class of TestFile, but can't because Groovy has issues with inner classes as delegates.
 */
class TestWorkspaceBuilder {
    def TestFile baseDir

    def TestWorkspaceBuilder(TestFile baseDir) {
        this.baseDir = baseDir
    }

    def apply(Closure cl) {
        cl.delegate = this
        cl.resolveStrategy = Closure.DELEGATE_FIRST
        cl()
    }

    def file(String name) {
        TestFile file = baseDir.file(name)
        file.write('some content')
        file
    }

    def setMode(int mode) {
        baseDir.mode = mode
    }

    def methodMissing(String name, Object args) {
        if (args.length == 1 && args[0] instanceof Closure) {
            baseDir.file(name).create(args[0])
        }
        else {
            throw new MissingMethodException(name, getClass(), args)
        }
    }
}
