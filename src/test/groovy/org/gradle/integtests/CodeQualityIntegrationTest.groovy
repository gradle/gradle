package org.gradle.integtests

import org.junit.Test
import static org.gradle.util.Matchers.*
import static org.hamcrest.Matchers.*

class CodeQualityIntegrationTest extends AbstractIntegrationTest {
    @Test
    public void handlesEmptyProjects() {
        testFile('build.gradle') << '''
usePlugin 'groovy'
usePlugin 'code-quality'
'''
        inTestDirectory().withTasks('check').run()
    }

    @Test
    public void generatesReportForJavaSource() {
        testFile('build.gradle') << '''
usePlugin 'java'
usePlugin 'code-quality'
'''
        writeCheckstyleConfig()

        testFile('src/main/java/org/gradle/Class1.java') << 'package org.gradle; class Class1 { }'
        testFile('src/test/java/org/gradle/TestClass1.java') << 'package org.gradle; class TestClass1 { }'

        inTestDirectory().withTasks('check').run()

        testFile('build/checkstyle/main.xml').assertContents(containsLine(containsString('org/gradle/Class1.java')))
        testFile('build/checkstyle/test.xml').assertContents(containsLine(containsString('org/gradle/TestClass1.java')))
    }

    @Test
    public void generatesReportForJavaSourceInGroovySourceDirs() {
        testFile('build.gradle') << '''
usePlugin 'groovy'
usePlugin 'code-quality'
'''
        writeCheckstyleConfig()

        testFile('src/main/groovy/org/gradle/Class1.java') << 'package org.gradle; class Class1 { }'
        testFile('src/test/groovy/org/gradle/TestClass1.java') << 'package org.gradle; class TestClass1 { }'

        inTestDirectory().withTasks('check').run()

        testFile('build/checkstyle/main.xml').assertContents(containsLine(containsString('org/gradle/Class1.java')))
        testFile('build/checkstyle/test.xml').assertContents(containsLine(containsString('org/gradle/TestClass1.java')))
    }

    @Test
    public void checkstyleOnlyChecksJavaSource() {
        testFile('build.gradle') << '''
usePlugin 'groovy'
usePlugin 'code-quality'
'''
        writeCheckstyleConfig()

        testFile('src/main/groovy/org/gradle/Class1.java') << 'package org.gradle; class Class1 { }'
        testFile('src/main/groovy/org/gradle/Class2.java') << 'package org.gradle; class Class2 { }'
        testFile('src/main/groovy/org/gradle/class3.groovy') << 'package org.gradle; class class3 { }'

        inTestDirectory().withTasks('checkstyle').run()

        testFile('build/checkstyle/main.xml').assertExists()
    }

    @Test
    public void checkstyleViolationBreaksBuild() {
        testFile('build.gradle') << '''
usePlugin 'groovy'
usePlugin 'code-quality'
'''
        writeCheckstyleConfig()

        testFile('src/main/java/org/gradle/class1.java') << 'package org.gradle; class class1 { }'
        testFile('src/main/groovy/org/gradle/class2.java') << 'package org.gradle; class class2 { }'

        ExecutionFailure failure = inTestDirectory().withTasks('check').runWithFailure()
        failure.assertHasDescription('Execution failed for task \':checkstyle\'')
        failure.assertThatCause(startsWith('Checkstyle check violations were found in main java source. See the report at'))

        testFile('build/checkstyle/main.xml').assertExists()
    }

    @Test
    public void generatesReportForGroovySource() {
        testFile('build.gradle') << '''
usePlugin 'groovy'
usePlugin 'code-quality'
'''
        writeCodeNarcConfigFile()

        testFile('src/main/groovy/org/gradle/Class1.groovy') << 'package org.gradle; class Class1 { }'
        testFile('src/test/groovy/org/gradle/TestClass1.groovy') << 'package org.gradle; class TestClass1 { }'

        inTestDirectory().withTasks('check').run()

        testFile('build/reports/codenarc/main.html').assertExists()
        testFile('build/reports/codenarc/test.html').assertExists()
    }

    @Test
    public void codeNarcOnlyChecksGroovySource() {
        testFile('build.gradle') << '''
usePlugin 'groovy'
usePlugin 'code-quality'
'''

        writeCodeNarcConfigFile()

        testFile('src/main/groovy/org/gradle/class1.java') << 'package org.gradle; class class1 { }'
        testFile('src/main/groovy/org/gradle/Class2.groovy') << 'package org.gradle; class Class2 { }'

        inTestDirectory().withTasks('codenarc').run()

        testFile('build/reports/codenarc/main.html').assertExists()
    }

    @Test
    public void codeNarcViolationBreaksBuild() {
        testFile('build.gradle') << '''
usePlugin 'groovy'
usePlugin 'code-quality'
'''

        writeCodeNarcConfigFile()

        testFile('src/main/groovy/org/gradle/class1.groovy') << 'package org.gradle; class class1 { }'

        ExecutionFailure failure = inTestDirectory().withTasks('check').runWithFailure()
        failure.assertHasDescription('Execution failed for task \':codenarc\'')
        failure.assertThatCause(startsWith('CodeNarc check violations were found in main groovy source. See the report at '))

        testFile('build/reports/codenarc/main.html').assertExists()
    }

    private TestFile writeCheckstyleConfig() {
        return testFile('config/checkstyle.xml') << '''
<!DOCTYPE module PUBLIC
        "-//Puppy Crawl//DTD Check Configuration 1.2//EN"
        "http://www.puppycrawl.com/dtds/configuration_1_2.dtd">
<module name="Checker">
    <module name="TreeWalker">
        <module name="TypeName"/>
    </module>
</module>'''
    }

    private TestFile writeCodeNarcConfigFile() {
        return testFile('config/codenarc.xml') << '''
<ruleset xmlns="http://codenarc.org/ruleset/1.0"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:schemaLocation="http://codenarc.org/ruleset/1.0 http://codenarc.org/ruleset-schema.xsd"
        xsi:noNamespaceSchemaLocation="http://codenarc.org/ruleset-schema.xsd">
    <ruleset-ref path='rulesets/naming.xml'/>
</ruleset>
'''
    }

}
