/*
 * Copyright 2008 the original author or authors.
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
package org.gradle.integtests;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

@RunWith(DistributionIntegrationTestRunner.class)
public class DynamicObjectIntegrationTest {
    // Injected by test runner
    private GradleDistribution dist;
    private GradleExecuter executer;

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
                "import static org.junit.Assert.*",
                "childProperty = 'child'",
                "sharedProperty = 'shared'",
                "task testTask << {",
                "  new Reporter().checkProperties(project)",
                "}",
                // Use a separate class, to isolate Project from the script
                "class Reporter {",
                "  def checkProperties(object) {",
                "    assertEquals('root', object.rootProperty)",
                "    assertEquals('child', object.childProperty)",
                "    assertEquals('shared', object.sharedProperty)",
                "    assertEquals('convention', object.conventionProperty)",
                "    assertEquals(':child:testTask', object.testTask.path)",
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
                "import static org.junit.Assert.*",
                "def childMethod(p) { 'child' + p }",
                "def sharedMethod(p) { 'shared' + p }",
                "task testTask << {",
                "  new Reporter().checkMethods(project)",
                "}",
                // Use a separate class, to isolate Project from the script
                "class Reporter {",
                "  def checkMethods(object) {",
                "    assertEquals('rootMethod', object.rootMethod('Method'))",
                "    assertEquals('childMethod', object.childMethod('Method'))",
                "    assertEquals('sharedMethod', object.sharedMethod('Method'))",
                "    assertEquals('conventionMethod', object.conventionMethod('Method'))",
                "    object.testTask { assertEquals(':child:testTask', delegate.path) }",
                "    try { object.rootTask { }; fail() } catch (MissingMethodException e) { }",
                "  }",
                "}"
        );

        executer.inDirectory(testDir).withTasks("testTask").run();
    }

    @Test
    public void canAddDynamicPropertiesToTasks() {
        TestFile testDir = dist.getTestDir();
        testDir.file("build.gradle").writelns(
                "task defaultTask {",
                "    custom = 'value'",
                "}",
                "task javaTask(type: Copy) {",
                "    custom = 'value'",
                "}",
                "task groovyTask(type: Zip) {",
                "    custom = 'value'",
                "}",
                "defaultTask.custom = 'another value'",
                "javaTask.custom = 'another value'",
                "groovyTask.custom = 'another value'"
        );

        executer.inDirectory(testDir).withTasks("defaultTask").run();
    }
}
