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

package org.gradle.test.fixtures.junitplatform

import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Unroll

import static JUnitPlatformTestRewriter.LATEST_JUPITER_VERSION

@CleanupTestDirectory
class JUnitPlatformTestRewriterTest extends Specification {

    @Rule
    final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()

    def 'build.gradle should be rewritten'() {
        given:
        temporaryFolder.testDirectory.file('build.gradle') << '''
dependencies { testCompile 'junit:junit:4.12' }
'''
        when:
        JUnitPlatformTestRewriter.rewriteBuildFile(temporaryFolder.testDirectory)

        then:
        temporaryFolder.testDirectory.file('build.gradle').text.contains(
            "testCompile 'org.junit.jupiter:junit-jupiter-api:${LATEST_JUPITER_VERSION}','org.junit.jupiter:junit-jupiter-engine:${LATEST_JUPITER_VERSION}'")
    }

    @Unroll
    def 'java source files should be rewritten'() {
        given:
        temporaryFolder.testDirectory.file('src/test/java/Test.java') << oldText

        when:
        JUnitPlatformTestRewriter.rewriteJavaFiles(temporaryFolder.testDirectory)

        then:
        temporaryFolder.testDirectory.file('src/test/java/Test.java').text == newText

        where:
        oldText | newText
        OLD1    | NEW1
        OLD2    | NEW2
        OLD3    | NEW3
    }

    static final String OLD1 = '''
package org.gradle;

import org.junit.Test;
import org.junit.*;

public class Test1 {
    @Test
    public void ok() {
        Assert.assertTrue(true);
    }
}
'''
    static final String NEW1 = '''
package org.gradle;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.*;

public class Test1 {
    @Test
    public void ok() {
        Assertions.assertTrue(true);
    }
}
'''
    static final String OLD2 = '''
import static org.junit.Assert.*;
import org.junit.After;

public class OkTest {
    @org.junit.Test
    public void ok() throws Exception {
        assertEquals("4.12", new org.junit.runner.JUnitCore().getVersion());
    }
    
    @After
    public void broken() {
        fail("failed");
    }
    
    @AfterClass
    public void clean() {
    }
    
    @org.junit.AfterClass
    public void clean() {
    }
}
'''
    static final String NEW2 = '''
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.AfterEach;

public class OkTest {
    @org.junit.jupiter.api.Test
    public void ok() throws Exception {
        assertEquals("4.12", new org.junit.runner.JUnitCore().getVersion());
    }
    
    @AfterEach
    public void broken() {
        fail("failed");
    }
    
    @AfterAll
    public void clean() {
    }
    
    @org.junit.jupiter.api.AfterAll
    public void clean() {
    }
}
'''
    static final String OLD3 = '''
import org.junit.Ignore;
import org.junit.Test;

public class Junit4Test {
    @Test
    public void ok() {
    }

    @Test
    @Ignore
    public void broken() {
        throw new RuntimeException();
    }
}
'''
    static final String NEW3 = '''
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class Junit4Test {
    @Test
    public void ok() {
    }

    @Test
    @Disabled
    public void broken() {
        throw new RuntimeException();
    }
}
'''

}
