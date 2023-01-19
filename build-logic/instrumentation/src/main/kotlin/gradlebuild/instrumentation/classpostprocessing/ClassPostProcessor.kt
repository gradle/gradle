/*
 * Copyright 2023 the original author or authors.
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

package gradlebuild.instrumentation.classpostprocessing

import org.objectweb.asm.ClassVisitor
import java.io.Serializable


/**
 * Defines a way to post-process a set of compiled classes, listed by the names in [classNamesToProcess], using [ClassVisitor]s produced by [classVisitorForClass].
 */
interface ClassPostProcessor : Serializable {
    /**
     * Specifies the qualified names of the classes which this [ClassPostProcessor] can process.
     */
    val classNamesToProcess: Iterable<String>

    /**
     * Provides a transforming [ClassVisitor] implementation for the class by the [className],
     * based on the provided [classVisitor] that writes the resulting class.
     *
     * The [className] may only be one of the [classNamesToProcess].
     */
    fun classVisitorForClass(className: String, classVisitor: ClassVisitor): ClassVisitor
}
