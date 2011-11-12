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
package org.gradle.integtests.plugins

import static org.gradle.util.Matchers.*
import static org.hamcrest.Matchers.*

import org.gradle.integtests.fixtures.ExecutionFailure
import org.gradle.integtests.fixtures.GradleDistributionExecuter.Executer
import org.gradle.integtests.fixtures.internal.AbstractIntegrationTest
import org.junit.Before
import org.junit.Test


class PMDIntegrationTest extends AbstractIntegrationTest {
	PMDIntegrationTest() {
		executer.type = Executer.forking
	}
	
    @Before
    void newBuild() {
        writeBuildFile()
    }
    
    @Test
    void check_EmptyProject_Success() {
        inTestDirectory().withTasks('check').run()
    }
    
    @Test
    void check_WithSource_GenerateReport() {
        testFile('build.gradle') << '''\
        pmdMain.ignoreFailures = true
        pmdTest.ignoreFailures = true
        '''
        testFile('src/main/java/org/gradle/Class1.java') << 'package org.gradle; class Class1 { {} public boolean equals(Object arg) { return true; } }'
        testFile('src/test/java/org/gradle/Class1Test.java') << 'package org.gradle; class Class1Test { {} public boolean equals(Object arg) { return true; } }'
        testFile('config/pmd/rulesets.xml') << '<ruleset name="Custom ruleset"> <rule ref="rulesets/basic.xml" /> </ruleset>'
        
        inTestDirectory().withTasks('check').run()
        
        testFile('build/pmd/main.xml').assertContents(containsString('org/gradle/Class1'))
        testFile('build/pmd/test.xml').assertContents(containsString('org/gradle/Class1Test'))
    }
    
    @Test
    void check_WithViolation_FailsBuild() {
        testFile('src/main/java/org/gradle/Class1.java') << 'package org.gradle; class Class1 { {} public boolean equals(Object arg) { return true; } }'
        testFile('config/pmd/rulesets.xml') << '<ruleset name="Custom ruleset"> <rule ref="rulesets/basic.xml" /> </ruleset>'
        
        ExecutionFailure failure = inTestDirectory().withTasks('check').runWithFailure()
		failure.assertHasDescription('Execution failed for task \':pmdMain\'')
		failure.assertThatCause(containsString('PMD found 2 rule violations'))
		
		testFile('build/pmd/main.xml').assertContents(containsString('org/gradle/Class1'))
    }
    
    @Test
    void check_OverrideRulesetsAndViolation_GeneratesReport() {
        testFile('build.gradle') << '''\
		pmdMain.ignoreFailures = true
        pmdTest.ignoreFailures = true
        pmd.rulesets = ['rulesets/basic.xml']
        '''
        
        testFile('src/main/java/org/gradle/Class1.java') << 'package org.gradle; class Class1 { {} public boolean equals(Object arg) { return true; } }'
        testFile('src/test/java/org/gradle/Class1Test.java') << 'package org.gradle; class Class1Test { {} public boolean equals(Object arg) { return true; } }'
        
		inTestDirectory().withTasks('check').run()
		
		testFile('build/pmd/main.xml').assertContents(containsString('org/gradle/Class1'))
		testFile('build/pmd/test.xml').assertContents(containsString('org/gradle/Class1Test'))
    }
    
    private void writeBuildFile() {
        testFile('build.gradle') << """\
        apply plugin: 'groovy'
        apply plugin: 'pmd'
        
        repositories {
            mavenCentral()
        }
        
        dependencies {
            pmd group:'pmd', name:'pmd', version:'4.2.5'
        }
        """
    }
}
