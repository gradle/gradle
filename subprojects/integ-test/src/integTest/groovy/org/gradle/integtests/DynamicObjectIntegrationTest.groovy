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
package org.gradle.integtests

import org.gradle.integtests.fixtures.GradleDistribution
import org.gradle.integtests.fixtures.GradleDistributionExecuter
import org.gradle.util.TestFile
import org.junit.Rule
import org.junit.Test

class DynamicObjectIntegrationTest {
    @Rule public final GradleDistribution dist = new GradleDistribution()
    @Rule public final GradleDistributionExecuter executer = new GradleDistributionExecuter()

    @Test
    public void canAddDynamicPropertiesToProject() {
        TestFile testDir = dist.getTestDir();
        testDir.file("settings.gradle").writelns("include 'child'");
        testDir.file("build.gradle").writelns(
                "rootProperty = 'root'",
                "sharedProperty = 'ignore me'",
                "convention.plugins.test = new ConventionBean()",
                "task rootTask",
                "task testTask",
                "class ConventionBean { def getConventionProperty() { 'convention' } }"
        );
        testDir.file("child/build.gradle").writelns(
                "childProperty = 'child'",
                "sharedProperty = 'shared'",
                "task testTask << {",
                "  new Reporter().checkProperties(project)",
                "}",
                "assert 'root' == rootProperty",
                "assert 'root' == property('rootProperty')",
                "assert 'root' == properties.rootProperty",
                "assert 'child' == childProperty",
                "assert 'child' == property('childProperty')",
                "assert 'child' == properties.childProperty",
                "assert 'shared' == sharedProperty",
                "assert 'shared' == property('sharedProperty')",
                "assert 'shared' == properties.sharedProperty",
                "assert 'convention' == conventionProperty",
                // Use a separate class, to isolate Project from the script
                "class Reporter {",
                "  def checkProperties(object) {",
                "    assert 'root' == object.rootProperty",
                "    assert 'child' == object.childProperty",
                "    assert 'shared' == object.sharedProperty",
                "    assert 'convention' == object.conventionProperty",
                "    assert ':child:testTask' == object.testTask.path",
                "    try { object.rootTask; fail() } catch (MissingPropertyException e) { }",
                "  }",
                "}"
        );

        executer.inDirectory(testDir).withTasks("testTask").run();
    }

    @Test
    public void canAddDynamicMethodsToProject() {
        TestFile testDir = dist.getTestDir();
        testDir.file("settings.gradle").writelns("include 'child'");
        testDir.file("build.gradle").writelns(
                "def rootMethod(p) { 'root' + p }",
                "def sharedMethod(p) { 'ignore me' }",
                "convention.plugins.test = new ConventionBean()",
                "task rootTask",
                "task testTask",
                "class ConventionBean { def conventionMethod(name) { 'convention' + name } }"
        );
        testDir.file("child/build.gradle").writelns(
                "def childMethod(p) { 'child' + p }",
                "def sharedMethod(p) { 'shared' + p }",
                "task testTask << {",
                "  new Reporter().checkMethods(project)",
                "}",
                // Use a separate class, to isolate Project from the script
                "class Reporter {",
                "  def checkMethods(object) {",
                "    assert 'rootMethod' == object.rootMethod('Method')",
                "    assert 'childMethod' == object.childMethod('Method')",
                "    assert 'sharedMethod'== object.sharedMethod('Method')",
                "    assert 'conventionMethod' == object.conventionMethod('Method')",
                "    object.testTask { assert ':child:testTask' == delegate.path }",
                "    try { object.rootTask { }; fail() } catch (MissingMethodException e) { }",
                "  }",
                "}"
        );

        executer.inDirectory(testDir).withTasks("testTask").run();
    }

    @Test
    public void canAddPropertiesToProjectUsingGradlePropertiesFile() {
        TestFile testDir = dist.getTestDir();
        testDir.file("settings.gradle").writelns("include 'child'");
        testDir.file("gradle.properties") << '''
global=some value
'''
        testDir.file("build.gradle") << '''
assert 'some value' == global
assert hasProperty('global')
assert 'some value' == property('global')
assert 'some value' == properties.global
assert 'some value' == project.global
assert project.hasProperty('global')
assert 'some value' == project.property('global')
assert 'some value' == project.properties.global
'''
        testDir.file("child/gradle.properties") << '''
global=overridden value
'''
        testDir.file("child/build.gradle") << '''
assert 'overridden value' == global
'''

        executer.inDirectory(testDir).run();
    }

    @Test
    public void canAddDynamicPropertiesToCoreDomainObjects() {
        TestFile testDir = dist.getTestDir();
        testDir.file('build.gradle') << '''
            class Extension { def doStuff() { 'method' } }
            class GroovyTask extends DefaultTask { }

            task defaultTask {
                custom = 'value'
                convention.plugins.custom = new Extension()
            }
            task javaTask(type: Copy) {
                custom = 'value'
                convention.plugins.custom = new Extension()
            }
            task groovyTask(type: GroovyTask) {
                custom = 'value'
                convention.plugins.custom = new Extension()
            }
            configurations {
                test {
                    custom = 'value'
                    convention.plugins.custom = new Extension()
                }
            }
            dependencies {
                test('::name:') {
                    custom = 'value';
                    convention.plugins.custom = new Extension()
                }
                test(module('::other')) {
                    custom = 'value';
                    convention.plugins.custom = new Extension()
                }
                test(project(':')) {
                    custom = 'value';
                    convention.plugins.custom = new Extension()
                }
                test(files('src')) {
                    custom = 'value';
                    convention.plugins.custom = new Extension()
                }
            }
            repositories {
                custom = 'repository'
                convention.plugins.custom = new Extension()
            }
            defaultTask.custom = 'another value'
            javaTask.custom = 'another value'
            groovyTask.custom = 'another value'
            assert !project.hasProperty('custom')
            assert defaultTask.hasProperty('custom')
            assert defaultTask.custom == 'another value'
            assert defaultTask.doStuff() == 'method'
            assert javaTask.doStuff() == 'method'
            assert groovyTask.doStuff() == 'method'
            assert configurations.test.hasProperty('custom')
            assert configurations.test.custom == 'value'
            assert configurations.test.doStuff() == 'method'
            configurations.test.dependencies.each {
                assert it.hasProperty('custom')
                assert it.custom == 'value'
                assert it.getProperty('custom') == 'value'
            }
            assert repositories.hasProperty('custom')
            assert repositories.custom == 'repository'
            assert repositories.doStuff() == 'method'
            repositories {
                assert custom == 'repository'
                assert doStuff() == 'method'
            }
'''

        executer.inDirectory(testDir).withTasks("defaultTask").run();
    }

    @Test
    public void canInjectMethodsFromParentProject() {
        TestFile testDir = dist.getTestDir();
        testDir.file("settings.gradle").writelns("include 'child'");
        testDir.file("build.gradle").writelns(
                "subprojects {",
                "  injectedMethod = { project.name }",
                "}"
        );
        testDir.file("child/build.gradle").writelns(
                "task testTask << {",
                "   assert injectedMethod() == 'child'",
                "}"
        );

        executer.inDirectory(testDir).withTasks("testTask").run();
    }
}
