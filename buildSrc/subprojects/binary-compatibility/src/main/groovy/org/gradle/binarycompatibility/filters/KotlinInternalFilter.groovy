/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.binarycompatibility.filters

import japicmp.filter.BehaviorFilter
import japicmp.filter.ClassFilter
import japicmp.filter.FieldFilter

import javassist.CtBehavior
import javassist.CtClass
import javassist.CtField

import org.gradle.binarycompatibility.metadata.KotlinMetadataQueries


class KotlinInternalFilter implements ClassFilter, FieldFilter, BehaviorFilter {

    private KotlinMetadataQueries metadata = KotlinMetadataQueries.INSTANCE

    @Override
    boolean matches(CtClass ctClass) {
        return metadata.queryKotlinMetadata(ctClass, false, metadata.isKotlinInternal(ctClass))
    }

    @Override
    boolean matches(CtField ctField) {
        return metadata.queryKotlinMetadata(ctField.declaringClass, false, metadata.isKotlinInternal(ctField))
    }

    @Override
    boolean matches(CtBehavior ctBehavior) {
        return metadata.queryKotlinMetadata(ctBehavior.declaringClass, false, metadata.isKotlinInternal(ctBehavior))
    }
}
