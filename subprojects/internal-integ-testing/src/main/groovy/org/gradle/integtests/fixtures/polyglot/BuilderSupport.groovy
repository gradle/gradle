/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.integtests.fixtures.polyglot

import groovy.transform.CompileStatic
import groovy.transform.PackageScope

import java.lang.reflect.Array

@CompileStatic
@PackageScope
abstract class BuilderSupport {
    static <T> T applyConfiguration(Closure<?> cl, T target) {
        Closure<?> clone = (Closure<?>) cl.clone()
        clone.resolveStrategy = Closure.DELEGATE_FIRST
        clone.delegate = target
        clone.call(target)
        target
    }

    static String prettyPrint(String content) {
        int indent = 0
        content.split("[\r]?\n").collect { line ->
            String trim = line.trim()
            def out
            if (trim.count("}") > trim.count("{")) {
                indent--
            }
            out = ("    " * indent) + trim
            if (trim.count("{") > trim.count("}")) {
                indent++
            }
            out
        }.join("\n")
    }

    static Object unwrap(Object args) {
        if (args == null) {
            return null
        }
        if (args.class.isArray() && Array.getLength(args) == 1) {
            return Array.get(args, 0)
        }
        return args
    }
}
