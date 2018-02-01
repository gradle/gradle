package org.gradle.kotlin.dsl.fixtures

import org.gradle.util.GFileUtils
import org.junit.rules.TemporaryFolder

/**
 * A [TemporaryFolder] JUnit rule that fails the test if the
 * the temporary folder cannot be deleted when the test finishes.
 */
class ForcefullyDeletedTemporaryFolder : TemporaryFolder() {
    override fun delete() = GFileUtils.forceDelete(root)
}
