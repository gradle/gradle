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

package org.gradle.integtests;

import org.gradle.integtests.fixtures.AbstractIntegrationTest;
import org.junit.Test;

public class TaskDefinitionIntegrationTest extends AbstractIntegrationTest {
    @Test
    public void canDefineTasksUsingTaskKeywordAndIdentifier() {
        testFile("build.gradle").writelns(
                "task nothing",
                "task withAction << { }",
                "task emptyOptions()",
                "task task",
                "task withOptions(dependsOn: [nothing, withAction, emptyOptions, task])",
                "task withOptionsAndAction(dependsOn: withOptions) << { }");
        inTestDirectory().expectDeprecationWarning().withTasks("withOptionsAndAction").run().assertTasksExecuted(":emptyOptions", ":nothing",
                ":task", ":withAction", ":withOptions", ":withOptionsAndAction");
    }

    @Test
    public void canDefineTasksUsingTaskKeywordAndGString() {
        testFile("build.gradle").writelns(
                "ext.v = 'Task'",
                "task \"nothing$v\"",
                "task \"withAction$v\" << { }",
                "task \"emptyOptions$v\"()",
                "task \"withOptions$v\"(dependsOn: [nothingTask, withActionTask, emptyOptionsTask])",
                "task \"withOptionsAndAction$v\"(dependsOn: withOptionsTask) << { }");
        inTestDirectory().expectDeprecationWarning().withTasks("withOptionsAndActionTask").run().assertTasksExecuted(":emptyOptionsTask",
                ":nothingTask", ":withActionTask", ":withOptionsTask", ":withOptionsAndActionTask");
    }

    @Test
    public void canDefineTasksUsingTaskKeywordAndString() {
        testFile("build.gradle").writelns(
                "task 'nothing'",
                "task 'withAction' << { }",
                "task 'emptyOptions'()",
                "task 'withOptions'(dependsOn: [nothing, withAction, emptyOptions])",
                "task 'withOptionsAndAction'(dependsOn: withOptions) << { }");
        inTestDirectory().expectDeprecationWarning().withTasks("withOptionsAndAction").run().assertTasksExecuted(":emptyOptions", ":nothing",
                ":withAction", ":withOptions", ":withOptionsAndAction");
    }

    @Test
    public void canDefineTasksInNestedBlocks() {
        testFile("build.gradle").writelns(
                "2.times { task \"dynamic$it\" }",
                "if (dynamic0) { task inBlock }",
                "def task() { task inMethod }",
                "task()", "def cl = { -> task inClosure }",
                "cl()",
                "task all(dependsOn: [dynamic0, dynamic1, inBlock, inMethod, inClosure])");
        inTestDirectory().withTasks("all").run().assertTasksExecuted(":dynamic0", ":dynamic1", ":inBlock", ":inClosure",
                ":inMethod", ":all");
    }

    @Test
    public void canDefineTasksUsingTaskMethodExpression() {
        testFile("build.gradle").writelns(
                "ext.a = 'a' == 'b' ? null: task(withAction) << { }",
                "a = task(nothing)",
                "a = task(emptyOptions())", "ext.taskName = 'dynamic'",
                "a = task(\"$taskName\") << { }",
                "a = task('string')",
                "a = task('stringWithAction') << { }",
                "a = task('stringWithOptions', description: 'description')",
                "a = task('stringWithOptionsAndAction', description: 'description') << { }",
                "a = task(withOptions, description: 'description')",
                "a = task(withOptionsAndAction, description: 'description') << { }",
                "a = task(anotherWithAction).doFirst\n{}",
                "task all(dependsOn: tasks as List)");
        inTestDirectory().expectDeprecationWarning().withTasks("all").run().assertTasksExecuted(":anotherWithAction", ":dynamic", ":emptyOptions",
                ":nothing", ":string", ":stringWithAction", ":stringWithOptions", ":stringWithOptionsAndAction",
                ":withAction", ":withOptions", ":withOptionsAndAction", ":all");
    }

    @Test
    public void canConfigureTasksWhenTheyAreDefined() {
        testFile("build.gradle").writelns(
                "task withDescription { description = 'value' }",
                "task(asMethod)\n{ description = 'value' }",
                "task asStatement(type: TestTask) { property = 'value' }",
                "task \"dynamic\"(type: TestTask) { property = 'value' }",
                "ext.v = task(asExpression, type: TestTask) { property = 'value' }",
                "task(postConfigure, type: TestTask).configure { property = 'value' }",
                "[asStatement, dynamic, asExpression, postConfigure].each { ",
                "    assert 'value' == it.property",
                "}",
                "[withDescription, asMethod].each {",
                "    assert 'value' == it.description",
                "}",
                "task all(dependsOn: tasks as List)",
                "class TestTask extends DefaultTask { String property }");
        inTestDirectory().withTasks("all").run();
    }

    @Test
    public void doesNotHideLocalMethodsAndVariables() {
        testFile("build.gradle").writelns(
                "String name = 'a'; task name",
//                "taskNameVar = 'b'; task taskNameVar",
                "def taskNameMethod(String name = 'c') { name } ",
//                "task taskNameMethod",
                "task taskNameMethod('d')",
                "def addTaskMethod(String methodParam) { task methodParam }",
                "addTaskMethod('e')",
                "def addTaskWithClosure(String methodParam) { task(methodParam) { ext.property = 'value' } }",
                "addTaskWithClosure('f')",
                "def addTaskWithMap(String methodParam) { task(methodParam, description: 'description') }",
                "addTaskWithMap('g')",
                "ext.cl = { String taskNameParam -> task taskNameParam }",
                "cl.call('h')",
                "cl = { String taskNameParam -> task(taskNameParam) { ext.property = 'value' } }",
                "cl.call('i')",
                "assert 'value' == f.property",
                "assert 'value' == i.property",
                "task all(dependsOn: tasks as List)");
        inTestDirectory().withTasks("all").run().assertTasksExecuted(":a", ":d", ":e", ":f", ":g", ":h", ":i", ":all");
    }
}
