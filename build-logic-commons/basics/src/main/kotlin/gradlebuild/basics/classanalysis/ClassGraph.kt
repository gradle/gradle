/*
 * Copyright 2018 the original author or authors.
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

package gradlebuild.basics.classanalysis


class ClassGraph(
    private val keepPackages: NameMatcher,
    private val unshadedPackages: NameMatcher,
    private val ignorePackages: NameMatcher,
    shadowPackage: String
) {

    private
    val classes: MutableMap<String, ClassDetails> = linkedMapOf()

    val entryPoints: MutableSet<ClassDetails> = linkedSetOf()

    val resources: MutableSet<String> = linkedSetOf()

    val transitiveResources: MutableSet<String> = linkedSetOf()

    private
    val shadowPackagePrefix =
        shadowPackage.takeIf(String::isNotEmpty)
            ?.let { it.replace('.', '/') + "/" }
            ?: ""

    operator fun get(className: String) =
        classes.computeIfAbsent(className) {
            val outputClassName = if (unshadedPackages.matches(className)) className else shadowPackagePrefix + className
            ClassDetails(outputClassName).also { classDetails ->
                if (keepPackages.matches(className) && !ignorePackages.matches(className)) {
                    entryPoints.add(classDetails)
                }
            }
        }

    fun getDependencies() = classes.map { it.value.outputClassFilename to it.value.dependencies.map { it.outputClassFilename } }.toMap()
}


class ClassDetails(val outputClassName: String) {
    var visited: Boolean = false
    val dependencies: MutableSet<ClassDetails> = linkedSetOf()
    val outputClassFilename
        get() = "$outputClassName.class"
}
