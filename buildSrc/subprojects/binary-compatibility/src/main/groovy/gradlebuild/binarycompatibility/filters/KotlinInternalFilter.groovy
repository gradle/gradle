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

package gradlebuild.binarycompatibility.filters

import gradlebuild.binarycompatibility.metadata.KotlinMetadataQueries
import japicmp.filter.BehaviorFilter
import japicmp.filter.ClassFilter
import japicmp.filter.FieldFilter
import javassist.CtBehavior
import javassist.CtClass
import javassist.CtField

/**
 * Matches Kotlin <code>internal</code> members.
 */
class KotlinInternalFilter implements ClassFilter, FieldFilter, BehaviorFilter {

    @Override
    boolean matches(CtClass ctClass) {
        return KotlinMetadataQueries.INSTANCE.isKotlinInternal(ctClass)
    }

    @Override
    boolean matches(CtField ctField) {
        return KotlinMetadataQueries.INSTANCE.isKotlinInternal(ctField)
    }

    @Override
    boolean matches(CtBehavior ctBehavior) {
        return KotlinMetadataQueries.INSTANCE.isKotlinInternal(ctBehavior)
    }
}
