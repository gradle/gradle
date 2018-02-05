package org.gradle.kotlin.dsl.fixtures


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
        previous = System.getProperty(testKitDirProperty, null)
        System.setProperty(testKitDirProperty, root.absolutePath)
    }

    override fun after() {
        if (previous == null) {
            System.clearProperty(testKitDirProperty)
        } else {
            System.setProperty(testKitDirProperty, previous)
        }
        super.after()
    }
}
