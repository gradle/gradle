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

package org.gradle.api.dsl;

/**
 * Tasks and objects in Gradle scripts are configurable via properties and methods.
 * Here's the breakdown of property kinds:
 *     <ul>
 *         <li>{@link SimpleProperty} - such property can be assigned and mutated (obviously if the object is mutable)</li>
 *         <li>{@link ConventionProperty} - such property can be assigned but cannot be mutated (even if the object is mutable!)</li>
 *     </ul>
 * Understanding of kins of properties is important mostly for collections. Consider this example:
 *
 * <pre>
 * someTask {
 *   //Simple properties can be assigned:
 *   simpleProperty = ['1.5']  //OK
 *
 *   //Simple properties can mutated:
 *   simpleProperty.add('1.5')  //OK
 *
 *   //Convention properties can be assigned:
 *   conventionProperty = ['a', 'b']  //OK
 *
 *   //Convention properties cannot be mutated, even if the object is mutable!
 *   conventionProperty.add('c')  //WRONG!
 *   //Instead, you can do the following:
 *   conventionProperty += 'c'  //OK
 * }
 * </pre>
 *
 * You may wonder why does Gradle separate convention properties from simple properties.
 * The reason for that is that internally, convention properties are evaluated 'lazily'.
 * This means that Gradle can configure tasks and objects with reasonable defaults
 * without worrying about the order of statements that configure the build. Example:
 *
 * <pre>
 * apply plugin: 'java'
 *
 * test {
 *   //test task has a testClassesDir convention property that is by default configured to:
 *   //testClassesDir = sourceSets.test.classesDir
 * }
 *
 * //what if someone reconfigured the test classes dir after the 'test' task was configured?
 * sourceSets.test.classesDir = new File(buildDir, 'test-classes')
 * //will the already-configured test.testClassesDir property point to a wrong folder?
 *
 * //Answer: It will all work fine!
 * //Thanks to the 'lazy' evaluation of the convention properties the user can reconfigure the sourceSets
 * //anywhere in the gradle script - and still the test.testClassesDir will point to the right folder.
 * </pre>
 *
 * Author: Szczepan Faber, created at: 4/14/11
 */
public class PropertyKinds {

    /**
     * A property that can be assigned and mutated (obviously if the object is mutable).
     * If the property is an immutable object like String or File it can only assigned.
     * <p>
     * See the docs for {@link PropertyKinds}
     */
    public static class SimpleProperty {}

    /**
     * A property that can be assigned but cannot be mutated (even if the object is mutable!).
     * Very important for collections because it prohibits
     * <p>
     * See the docs for {@link PropertyKinds}
     */
    public static class ConventionProperty {}

}