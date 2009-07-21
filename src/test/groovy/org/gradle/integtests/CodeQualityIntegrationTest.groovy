package org.gradle.integtests

import org.junit.Test
import static org.junit.Assert.*
import org.junit.Ignore

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
        testFile('config/checkstyle.xml') << '''
<!DOCTYPE module PUBLIC
        "-//Puppy Crawl//DTD Check Configuration 1.2//EN"
        "http://www.puppycrawl.com/dtds/configuration_1_2.dtd">
<module name="Checker">
    <module name="TreeWalker">
        <module name="EmptyBlock"/>
    </module>
</module>'''

        testFile('src/main/java/org/gradle/Class1.java') << 'package org.gradle; class Class1 { }'
        testFile('src/test/java/org/gradle/TestClass1.java') << 'package org.gradle; class TestClass1 { }'

        inTestDirectory().withTasks('check').run()

        testFile('build/checkstyle/main.xml').assertExists()
        testFile('build/checkstyle/test.xml').assertExists()
    }

    @Test @Ignore
    public void checkstyleOnlyChecksJavaSource() {
        fail()
    }

    @Test
    public void checkstyleViolationBreaksBuild() {
        testFile('build.gradle') << '''
usePlugin 'java'
usePlugin 'code-quality'
'''
        testFile('config/checkstyle.xml') << '''
<!DOCTYPE module PUBLIC
        "-//Puppy Crawl//DTD Check Configuration 1.2//EN"
        "http://www.puppycrawl.com/dtds/configuration_1_2.dtd">
<module name="Checker">
    <module name="TreeWalker">
        <module name="TypeName"/>
    </module>
</module>'''

        testFile('src/main/java/org/gradle/class1.java') << 'package org.gradle; class class1 { }'

        ExecutionFailure failure = inTestDirectory().withTasks('check').runWithFailure()
        failure.assertHasDescription('Execution failed for task \':checkstyle\'')
        failure.assertHasCause('Got 1 errors and 0 warnings.')

        testFile('build/checkstyle/main.xml').assertExists()
    }

    @Test
    public void generatesReportForGroovySource() {
        testFile('build.gradle') << '''
usePlugin 'groovy'
usePlugin 'code-quality'
'''
        testFile('config/codenarc.xml') << '''
<ruleset xmlns="http://codenarc.org/ruleset/1.0"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:schemaLocation="http://codenarc.org/ruleset/1.0 http://codenarc.org/ruleset-schema.xsd"
        xsi:noNamespaceSchemaLocation="http://codenarc.org/ruleset-schema.xsd">
    <ruleset-ref path='rulesets/imports.xml'/>
</ruleset>
'''

        testFile('src/main/groovy/org/gradle/Class1.groovy') << 'package org.gradle; class Class1 { }'
        testFile('src/test/groovy/org/gradle/TestClass1.groovy') << 'package org.gradle; class TestClass1 { }'

        inTestDirectory().withTasks('check').run()

        testFile('build/reports/codenarc/main.html').assertExists()
        testFile('build/reports/codenarc/test.html').assertExists()
    }

    @Test @Ignore
    public void codeNarcOnlyChecksGroovySource() {
        fail()
    }

    @Test
    public void codeNarcViolationBreaksBuild() {
        testFile('build.gradle') << '''
usePlugin 'groovy'
usePlugin 'code-quality'
'''

        testFile('config/codenarc.xml') << '''
<ruleset xmlns="http://codenarc.org/ruleset/1.0"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:schemaLocation="http://codenarc.org/ruleset/1.0 http://codenarc.org/ruleset-schema.xsd"
        xsi:noNamespaceSchemaLocation="http://codenarc.org/ruleset-schema.xsd">
    <ruleset-ref path='rulesets/naming.xml'/>
</ruleset>
'''
        
        testFile('src/main/groovy/org/gradle/class1.groovy') << 'package org.gradle; class class1 { }'

        ExecutionFailure failure = inTestDirectory().withTasks('check').runWithFailure()
        failure.assertHasDescription('Execution failed for task \':codenarc\'')
        failure.assertHasCause('Exceeded maximum number of priority 2 violations: (p1=0; p2=1; p3=0)')

        testFile('build/reports/codenarc/main.html').assertExists()
    }
}
