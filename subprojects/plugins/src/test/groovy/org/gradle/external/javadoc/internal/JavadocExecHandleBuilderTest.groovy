/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.external.javadoc.internal

import org.gradle.external.javadoc.MinimalJavadocOptions
import org.gradle.internal.jvm.Jvm
import org.junit.Test
import spock.lang.Specification

import static org.junit.Assert.assertTrue

public class JavadocExecHandleBuilderTest extends Specification {

    private JavadocExecHandleBuilder javadocExecHandleBuilder = new JavadocExecHandleBuilder()

    def setup() {
        MinimalJavadocOptions minimalJavadocOptions = Mock()
        javadocExecHandleBuilder.options = minimalJavadocOptions
        javadocExecHandleBuilder.optionsFile = new File(".")
    }

    def testCheckExecutableAfterInit() {
        expect:
        javadocExecHandleBuilder.execHandle.executable == Jvm.current().javadocExecutable.absolutePath
    }

    def testCheckCustomExecutable() {
        String executable = "somepath"
        javadocExecHandleBuilder.executable = executable
        
        expect:
        javadocExecHandleBuilder.execHandle.executable == executable
    }

    def testSetNullExecDirectory() {
        when:
        javadocExecHandleBuilder.execDirectory(null)

        then:
        thrown(java.lang.IllegalArgumentException)
    }

    def testSetNotExistingDirectory() {
        when:
        javadocExecHandleBuilder.execDirectory(new File(".notExistingTestDirectoryX"));

        then:
        thrown(java.lang.IllegalArgumentException)
    }

    def testSetExistingExecDirectory() {
        File existingDirectory = new File(".existingDirectory");
        assertTrue(existingDirectory.mkdir());

        expect:
        try {
            javadocExecHandleBuilder.execDirectory(existingDirectory);
        }
        finally {
            existingDirectory.delete()
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetNullOptions() {
        when:
        javadocExecHandleBuilder.options(null);

        then:
        thrown(java.lang.IllegalArgumentException)
    }

    @Test
    public void testSetNotNullOptions() {
        MinimalJavadocOptions options = Mock()

        when:
        javadocExecHandleBuilder.options(options)

        then:
        true // no error
    }
}
