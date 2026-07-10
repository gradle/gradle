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

package org.gradle.internal.classloader;

/**
 * Test helper classes for concurrent class loading tests.
 * All inner classes are in the same package to test concurrent package definition.
 */
@SuppressWarnings("unused") // Nested classes used by string name
public class TransformingClassLoaderTestClasses {
    /**
     * Total number of nested classes defined.
     */
    public static final int NUM_CLASSES = 20;
    public static class Class01 {}
    public static class Class02 {}
    public static class Class03 {}
    public static class Class04 {}
    public static class Class05 {}
    public static class Class06 {}
    public static class Class07 {}
    public static class Class08 {}
    public static class Class09 {}
    public static class Class10 {}
    public static class Class11 {}
    public static class Class12 {}
    public static class Class13 {}
    public static class Class14 {}
    public static class Class15 {}
    public static class Class16 {}
    public static class Class17 {}
    public static class Class18 {}
    public static class Class19 {}
    public static class Class20 {}
}
