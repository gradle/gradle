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
 * ConventionProperty can be assigned but <b>cannot</b> be mutated (even if the object is mutable!)
 * <p>
 * Understanding convention properties is important mostly for collections
 * because one might want to mutate the collection but it wouldn't work
 * (actually mutating may work but it will be sensitive to the evaluation order).
 * <p>
 * Consider this example:
 *
 * <pre>
 * someTask {
 *   //Convention properties cannot be mutated,
 *   //even if the object is mutable!
 *   conventionProperty.add('c')  //WRONG!
 *
 *   //However, convention properties can be assigned:
 *   conventionProperty = ['a', 'b']  //OK
 *
 *   //Following may work but depends on the order of evaluation:
 *   conventionProperty -= 'a'  //SENSITIVE
 *
 *   //Simple properties can be mutated or assigned:
 *   simpleProperty = ['1.5']  //OK
 *   simpleProperty.add('1.5')  //OK
 * }
 * </pre>
 *
 * You may wonder why Gradle uses convention properties.
 * The reason for that is that internally, convention properties are evaluated 'lazily'.
 * This means that Gradle can configure tasks and objects with reasonable defaults
 * without worrying about the order of statements that configure the build. Example:
 *
 * <pre>
 * apply plugin: 'java'
 *
 * test {
 *   //test task has a testClassesDir convention property
 *   //that is by default configured to 'test classes dir'
 *   //testClassesDir = sourceSets.test.classesDir
 * }
 *
 * //what if someone reconfigured the 'test classes dir'
 * //after the 'test' task was configured? Like that:
 * sourceSets.test.classesDir = new File(buildDir, 'test-classes')
 *
 * //will the already-configured test.testClassesDir property
 * //on a 'test' task point to a wrong folder?
 * </pre>
 *
 * Answer: It will all work fine!
 * <p>
 * Thanks to the 'lazy' evaluation of the convention properties
 * the user can reconfigure the sourceSets anywhere in the gradle script -
 * and still the test.testClassesDir will point to the right folder.
 */
public class ConventionProperty {}