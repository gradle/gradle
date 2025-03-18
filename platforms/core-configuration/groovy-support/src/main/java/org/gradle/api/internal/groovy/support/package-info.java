/*
 * Copyright 2025 the original author or authors.
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

/**
 * Groovy-specific enhancements to various Gradle model types.
 *
 * <h2>Compound assignment support</h2>
 * <h3>Implementation overview</h3>
 * <p>
 * Groovy supports multiple compound assignment operators (like {@code +=}, {@code -=}) for common types like List out of the box.
 * However, unlike Kotlin, from the point of view of operator overloading,
 * the compound assignment is not really an operator of its own, but a combination of assignment and the standalone operator.
 * So, in order to support {@code +=} for your type, you need to define {@code plus()} method, which will also give support of {@code +}.
 * <p>
 * For some Gradle types, we want to support {@code +=} in a special way, which isn't compatible with common Groovy behavior.
 * In particular, we want to emulate Groovy properties with {@code Property} type.
 * For that we need to overload {@code +=} in a special way, to distinguish it from a standalone {@code +} use.
 * In other words, {@code a += b} should work but {@code a = a + b} shouldn't.
 * <p>
 * We do this by utilizing an AST transformation.
 * The source code of the transformation is in {@link org.gradle.api.internal.groovy.support.CompoundAssignmentTransformer}.
 * It also relies on a number of extension methods, support classes and interfaces that your type has to provide.
 * <p>
 * Compound assignments can be used in two different contexts:
 * <ul>
 *     <li>
 *         Updating a variable:
 *         <pre>
 *  def foo = objects.listProperty(String)
 *  foo += ["a"]
 *         </pre>
 *         In this case, {@code foo} <b>reference</b> is updated with a (processed) result of {@code foo + ["a"]} expression.
 *         It is not possible to intercept and handle the assignment part.
 *     </li>
 *     <li>
 *         Updating a property of a Gradle-managed class:
 *         <pre>
 *  abstract class Bar {
 *      abstract ListProperty&lt;String&gt; getFoo()
 *  }
 *  def bar = objects.newInstance(Bar)
 *  bar.foo += ["a"]
 *         </pre>
 *         In this case, Gradle generates a synthetic setter that invokes {@code bar.foo.setFromAnyValue(bar.foo + ["a"])}.
 *         No references are being reassigned.
 *     </li>
 * </ul>
 * In both cases, source transformation kicks in and allows the implementation of {@code foo} to replace itself with something that knows how to handle {@code +} even if the {@code foo} itself cannot.
 *
 * <h3>How to override compound assignment for your type</h3>
 * <p>
 * Suppose you want to support {@code +=} expression for your type. You need too:
 * <ol>
 *     <li>
 *         Define a class that will represent the sum. It must be useful on its own, because that is what will be assigned to the LHS variable.
 *         This class must implement {@link org.gradle.api.internal.groovy.support.CompoundAssignmentResult} interface.
 *     </li>
 *     <li>
 *         If the class you enhancing implements {@code LazyGroovySupport} then you can add custom assignment handling in the {@code setFromAnyValue} method,
 *         by checking if the argument is of the type defined in the previous step. And if it doesn't then why do you bother with this stuff?
 *         For example, instead of assigning its value as a whole, you can append its RHS side to the state of the class.
 *         Do not rely on type alone, these sums may become detached from its original producer when created as part of a variable:
 *         {@code def foo = bar.foo; foo += ["baz"]; bar.foo = foo }
 *         <p>
 *         A general recommendation is to also detach the sum implementation from the producer when {@code assignmentComplete} method is called:
 *         it means that the object is out of scope of the original {@code +=} expression.
 *     </li>
 *     <li>
 *         Define a <b>public</b> stand-in class that will handle {@code plus} operations.
 *         You need an overload for each right-hand-side operand type. These methods should return sum instances defined in step (1).
 *         The return type doesn't have to be the sum type, it can be its public supertype. The return type doesn't have to implement {@code Result}.
 *     </li>
 *     <li>
 *         Define a <a href="https://blog.mrhaki.com/2013/01/groovy-goodness-adding-extra-methods.html">public Groovy extension method</a> named {@code forCompoundAssignment}
 *         that accepts a <b>public</b> type of your class (e.g. {@code MapProperty} for {@code DefaultMapProperty} type) and returns a stand-in.
 *     </li>
 * </ol>
 * Note that the extension method and the stand-in type and its methods become <b>public APIs</b> and must follow binary compatibility policy,
 * though they aren't visible to the users. Compiled Groovy code may include references to these types and methods, especially if static compilation is used.
 *
 * <h3>Testing notes</h3>
 * You may need to clean {@code intTestHomeDir/} in the root project for your integration tests to pick up new type definitions.
 * <p>
 * Extension methods and stand-ins are not covered by existing public API checks.
 * So far we're using manual tests that verify class names and method signatures through Reflection API.
 * See {@code CompoundAssignmentBinaryCompatibilityFixture} for extra information.
 */
@org.jspecify.annotations.NullMarked
package org.gradle.api.internal.groovy.support;
