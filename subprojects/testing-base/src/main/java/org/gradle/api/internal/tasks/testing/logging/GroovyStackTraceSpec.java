/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.logging;

import org.gradle.api.specs.Spec;

import java.util.regex.Pattern;

// implementation based on Spock's StackTraceFilter class
public class GroovyStackTraceSpec implements Spec<StackTraceElement> {
    private static final Pattern INTERNAL_CLASSES = Pattern.compile(
            "org.codehaus.groovy.runtime\\..*"
                    + "|org.codehaus.groovy.reflection\\..*"
                    + "|org.codehaus.groovy\\..*MetaClass.*"
                    + "|groovy\\..*MetaClass.*"
                    + "|groovy.lang.MetaMethod"
                    + "|java.lang.reflect\\..*"
                    + "|sun.reflect\\..*"
    );

    public boolean isSatisfiedBy(StackTraceElement element) {
        return !isInternalClass(element) && !isGeneratedMethod(element);
    }

    private boolean isInternalClass(StackTraceElement element) {
        return INTERNAL_CLASSES.matcher(element.getClassName()).matches();
    }

    private boolean isGeneratedMethod(StackTraceElement element) {
        return element.getLineNumber() < 0;
    }
}
