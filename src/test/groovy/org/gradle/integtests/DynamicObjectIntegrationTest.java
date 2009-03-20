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

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

public class DynamicObjectIntegrationTest extends AbstractIntegrationTest {
    public static String result;

    @Before
    public void setUp() {
        result = null;
    }

    @Test
    public void canAddDynamicPropertiesToProject() {
        testFile("settings.gradle").writelns("include 'child'");
        testFile("build.gradle").writelns(
                "rootProperty = 'root'",
                "sharedProperty = 'ignore me'",
                "convention.plugins.test = new " + ConventionBean.class.getName() + "()",
                "createTask('rootTask')",
                "createTask('testTask')"
        );
        testFile("child/build.gradle").writelns(
                "childProperty = 'child'",
                "sharedProperty = 'shared'",
                "createTask('testTask') {",
                DynamicObjectIntegrationTestHelper.class.getName() + ".reportProperties(project)",
                "}"
        );

        inTestDirectory().withTasks("testTask").run();

        assertThat(result, equalTo("root,child,shared,convention,task ':child:testTask'"));
    }

    @Test
    public void canAddDynamicMethodsToProject() {
        testFile("settings.gradle").writelns("include 'child'");
        testFile("build.gradle").writelns(
                "def rootMethod(p) { 'root' + p }",
                "def sharedMethod(p) { 'ignore me' }",
                "convention.plugins.test = new " + ConventionBean.class.getName() + "()",
                "createTask('rootTask')",
                "createTask('testTask')"
        );
        testFile("child/build.gradle").writelns(
                "def childMethod(p) { 'child' + p }",
                "def sharedMethod(p) { 'shared' + p }",
                "createTask('testTask') {",
                DynamicObjectIntegrationTestHelper.class.getName() + ".reportMethods(project)",
                "}"
        );

        inTestDirectory().withTasks("testTask").run();

        assertThat(result, equalTo("rootMethod,childMethod,sharedMethod,conventionMethod,task ':child:testTask'"));
    }

}
