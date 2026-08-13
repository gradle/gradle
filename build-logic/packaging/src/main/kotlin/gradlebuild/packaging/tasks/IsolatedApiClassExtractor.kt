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

package gradlebuild.packaging.tasks

import java.io.File
import java.lang.reflect.InvocationTargetException
import java.net.URLClassLoader
import java.util.Optional
import java.util.function.Predicate


/**
 * Extracts API classes using the `org.gradle.internal.tools.api` extractor found on the given classpath.
 *
 * The extractor is loaded in a class loader that has no access to the Gradle distribution running this
 * build. Without that, `org.gradle.internal.tools.api` resolves parent-first against the running
 * distribution, and we silently extract with the extractor of whatever Gradle version happens to build
 * this source tree instead of the one built from it. Everything crossing the boundary here is either a
 * JDK type or accessed reflectively for that reason.
 */
internal
class IsolatedApiClassExtractor private constructor(
    private val classLoader: URLClassLoader,
    packages: Set<String>
) {

    private
    val extractorClass = classLoader.loadClass("org.gradle.internal.tools.api.ApiClassExtractor").also {
        // Nothing else fails when the isolation breaks, we just quietly extract with the wrong extractor
        check(it.classLoader === classLoader) {
            "$it was loaded by ${it.classLoader} instead of the isolated class loader, " +
                "so the extracted API would be the one of the Gradle distribution running this build"
        }
    }

    private
    val extractor = newExtractor(packages)

    private
    val extractApiClassFromMethod = extractorClass.getMethod("extractApiClassFrom", ByteArray::class.java)

    @Suppress("UNCHECKED_CAST")
    fun extractApiClassFrom(classBytes: ByteArray): Optional<ByteArray> =
        try {
            extractApiClassFromMethod.invoke(extractor, classBytes) as Optional<ByteArray>
        } catch (e: InvocationTargetException) {
            // Surface the extractor's own failure, not the reflective wrapper around it
            throw e.targetException
        }

    /**
     * Reflective equivalent of:
     * ```
     * ApiClassExtractor.withWriter(JavaApiMemberWriter.adapter())
     *     .includePackagePrivateMembers() // or .includePackagesMatching(packages::contains)
     *     .build()
     * ```
     */
    private
    fun newExtractor(packages: Set<String>): Any {
        val writerAdapterClass = classLoader.loadClass("org.gradle.internal.tools.api.ApiMemberWriterAdapter")
        val writerClass = classLoader.loadClass("org.gradle.internal.tools.api.impl.JavaApiMemberWriter")
        val writerAdapter = writerClass.getMethod("adapter").invoke(null)
        val builder = extractorClass.getMethod("withWriter", writerAdapterClass).invoke(null, writerAdapter)
        val builderClass = builder.javaClass
        val configuredBuilder = if (packages.isEmpty()) {
            builderClass.getMethod("includePackagePrivateMembers").invoke(builder)
        } else {
            builderClass.getMethod("includePackagesMatching", Predicate::class.java)
                .invoke(builder, Predicate<String> { it in packages })
        }
        return builderClass.getMethod("build").invoke(configuredBuilder)
    }

    companion object {

        /**
         * Runs [block] with an extractor loaded from [classpath], then closes the class loader.
         *
         * The class loader also closes when the extractor itself fails to come to life, which the
         * isolation check does on purpose. The extractor holds the class loader, so it stays inside
         * this scope.
         */
        fun <T> runUsing(classpath: Iterable<File>, packages: Set<String>, block: (IsolatedApiClassExtractor) -> T): T =
            URLClassLoader(
                classpath.map { it.toURI().toURL() }.toTypedArray(),
                ClassLoader.getPlatformClassLoader()
            ).use { classLoader ->
                block(IsolatedApiClassExtractor(classLoader, packages))
            }
    }
}
