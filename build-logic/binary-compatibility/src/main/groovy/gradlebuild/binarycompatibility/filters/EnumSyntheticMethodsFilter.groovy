/*
 * Copyright 2026 the original author or authors.
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

package gradlebuild.binarycompatibility.filters

import japicmp.filter.BehaviorFilter
import javassist.CtBehavior
import javassist.CtMethod

/**
 * Filters the compiler-synthesized {@code values()}, {@code valueOf(String)} and Kotlin's
 * {@code getEntries()} methods on enum types. These methods cannot be annotated in source, so
 * binary compatibility rules that require {@code @Incubating} or {@code @since} would otherwise
 * fire on changes the developer cannot fix.
 */
class EnumSyntheticMethodsFilter implements BehaviorFilter {
    @Override
    boolean matches(CtBehavior ctBehavior) {
        if (!(ctBehavior instanceof CtMethod)) {
            return false
        }
        if (!ctBehavior.declaringClass.isEnum()) {
            return false
        }
        def name = ctBehavior.name
        def paramTypes = ctBehavior.parameterTypes
        if (paramTypes.length == 0 && (name == "values" || name == "getEntries")) {
            return true
        }
        if (name == "valueOf" && paramTypes.length == 1 && paramTypes[0].name == "java.lang.String") {
            return true
        }
        return false
    }
}
