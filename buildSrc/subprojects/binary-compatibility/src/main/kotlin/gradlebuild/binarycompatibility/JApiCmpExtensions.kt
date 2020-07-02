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


@file:JvmName("JapicmpExtensions")

package gradlebuild.binarycompatibility

import japicmp.model.JApiBehavior
import japicmp.model.JApiClass
import japicmp.model.JApiCompatibility
import japicmp.model.JApiField


internal
val JApiCompatibility.jApiClass: JApiClass
    get() = when (this) {
        is JApiClass -> this
        is JApiField -> this.getjApiClass()
        is JApiBehavior -> this.getjApiClass()
        else -> throw IllegalStateException("Unsupported japicmp member type '${this::class}'")
    }


internal
val JApiClass.isKotlin: Boolean
    get() = newClass.orNull()?.isKotlin ?: false


internal
val JApiClass.simpleName: String
    get() = fullyQualifiedName.substringAfterLast(".")


internal
val JApiClass.packagePath: String
    get() = packageName.replace(".", "/")


internal
val JApiClass.packageName: String
    get() = fullyQualifiedName.substringBeforeLast(".")
