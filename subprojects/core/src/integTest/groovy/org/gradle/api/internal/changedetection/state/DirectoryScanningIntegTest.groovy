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

package org.gradle.api.internal.changedetection.state

import org.gradle.integtests.fixtures.Sample
import org.gradle.integtests.fixtures.daemon.DaemonIntegrationSpec
import org.gradle.util.GFileUtils
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Rule

// This fails with Java 1.8.0_05, but succeeds with 1.8.0_74
@Requires(adhoc = { TestPrecondition.JDK7_OR_LATER.isFulfilled() && System.getProperty('java.version') != '1.8.0_05' })
class DirectoryScanningIntegTest extends DaemonIntegrationSpec {
    @Rule
    public final Sample sample = new Sample(testDirectoryProvider, 'dirscanning', '.')

    boolean trackLocations = false
    boolean printCounts = true

    def setup() {
        executer.withArgument("--no-search-upward")
        if (trackLocations) {
            executer.withArgument("-PtrackLocations")
        }
    }

    // read serialized counts from file and transform paths to relative paths
    private Map<String, Integer> loadDirectoryScanCounts() {
        Map<File, Integer> scanCounts
        file('countDirectoryScans.ser').withInputStream {
            scanCounts = new ObjectInputStream(it).readObject()
        }
        // transform into relative paths
        scanCounts.collectEntries { File dir, Integer count ->
            [GFileUtils.relativePath(testDirectory, dir), count]
        }
    }

    private Map<File, List<Exception>> loadDirectoryScanLocations() {
        def file = file('directoryScanLocations.ser')
        if (file.exists()) {
            return file.withInputStream {
                new ObjectInputStream(it).readObject()
            }
        }
        null
    }

    private void printDirectoryScanLocations() {
        def locations = loadDirectoryScanLocations()
        locations?.each { k, v ->
            println "directory: ${GFileUtils.relativePath(testDirectory, k)}"
            v.each { Exception e ->
                println "Scan originated from:"
                printStackTrace(e, System.out)
                println()
            }
            println "-----------------------------------------"
        }
    }

    private void printStackTrace(Throwable exception, Appendable builder) {
        for (StackTraceElement element : exception.getStackTrace()) {
            if (element.getLineNumber() > 0) {
                builder.append(" at ")
                builder.append(element.getClassName())
                builder.append('(')
                builder.append(element.getFileName())
                builder.append(':')
                builder.append(String.valueOf(element.getLineNumber()))
                builder.append(')')
                builder.append('\n')
            }
            if (element.className == 'org.gradle.execution.DefaultBuildExecuter') {
                break
            }
        }
        builder.append('\n')
    }

    def "count directory scans for Java project with single source file"() {
        given:
        buildFile << '''
apply plugin: 'java'
'''
        file('src/main/java/Test.java') << 'class Test {}'
        expect:
        succeeds("build")
        checkDirectoryScanning()
    }

    def "count directory scans for Java project with single source file and test file"() {
        given:
        buildFile << '''
apply plugin: 'java'

repositories {
    mavenCentral()
}

dependencies {
    testCompile 'junit:junit:4.12'
}
'''
        file('src/main/java/Hello.java') << '''
public class Hello {
    private final String property;

    public Hello(String param) {
        this.property = param;
    }

    public String getProperty() {
        return property;
    }
}
'''
        file('src/main/test/HelloTest.java') << '''
import static org.junit.Assert.*;

public class HelloTest {
     private final Hello hello = new Hello("value");

     @org.junit.Test
     public void test() {
        assertEquals(hello.getProperty(), "value");
     }
}
'''
        expect:
        succeeds("test")
        checkDirectoryScanning()
    }

    private void checkDirectoryScanning(int maxScans = 3) {
        def scanCounts = loadDirectoryScanCounts()
        if (printCounts) {
            println "Directory scanning report:\n${scanCounts.sort { a, b -> a.value.compareTo(b.value) }.collect { k, v -> "$k\t$v times"}.join('\n')}"
        }
        if (trackLocations) {
            printDirectoryScanLocations()
        }
        scanCounts.each { String path, Integer count ->
            assert count <= maxScans: "$path has too many scans."
        }
    }
}
