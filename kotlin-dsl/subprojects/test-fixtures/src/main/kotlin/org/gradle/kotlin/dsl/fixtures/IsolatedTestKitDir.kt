package org.gradle.kotlin.dsl.fixtures

import java.io.File


private
const val testKitDirProperty = "org.gradle.testkit.dir"


/**
 * A [ForcefullyDeletedTemporaryFolder] JUnit rule that sets the [testKitDirProperty] around tests.
 */
open class IsolatedTestKitDir : ForcefullyDeletedTemporaryFolder() {

    private
    var previous: String? = null

    override fun before() {
        super.before()
        isolateTestKitDir()
    }

    override fun after() {
        restoreTestKitDir()
        super.after()
    }

    private
    fun isolateTestKitDir() {
        previous = System.getProperty(testKitDirProperty, null)
        System.setProperty(testKitDirProperty, root.absolutePath)
    }

    private
    fun restoreTestKitDir() =
        setOrClearProperty(testKitDirProperty, previous)
}


fun withIsolatedTestKitDir(dir: File, action: () -> Unit) =
    withSystemProperty(testKitDirProperty, dir.absolutePath, action)
