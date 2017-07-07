/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.binarycompatibility.rules;

import me.champeau.gradle.japicmp.report.*;
import japicmp.model.*;
import java.util.List;

public abstract class WithIncubatingCheck extends AbstractContextAwareViolationRule {
    private boolean isAnnotatedWithIncubating(JApiHasAnnotations member) {
        for (JApiAnnotation annotation : member.getAnnotations()) {
            if ("org.gradle.api.Incubating".equals(annotation.getFullyQualifiedName())) {
                return true;
            }
        }
        return false;
    }

    boolean isIncubating(JApiHasAnnotations member) {
        if (member instanceof JApiClass) {
            return isIncubating((JApiClass)member);
        } else if (member instanceof JApiMethod) {
            return isIncubating((JApiMethod)member);
        }
        return isAnnotatedWithIncubating(member);
    }

    boolean isIncubating(JApiClass clazz) {
        if (isAnnotatedWithIncubating(clazz)) {
            return true;
        }
        // all the methods need to be incubating
        List<JApiMethod> methods = clazz.getMethods();
        if (methods.isEmpty()) {
            return false;
        }
        for (JApiMethod method : methods) {
            if (!isIncubating(method)) {
                return false;
            }
        }
        return true;
    }

    boolean isIncubating(JApiMethod method) {
        return isAnnotatedWithIncubating(method) || isAnnotatedWithIncubating(method.getjApiClass());
    }
}
