package gradlebuild.performance

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File


class PerformanceTestPluginTest {

    @JvmField
    @Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `given a JUnit xml report, #allTestsWereSkipped returns true if @tests == @skipped and false otherwise`() {

        assert(
            allTestsWereSkipped(junitXmlWith(tests = "2", skipped = "2"))
        )

        assert(
            !allTestsWereSkipped(junitXmlWith(tests = "2", skipped = "1"))
        )
    }

    private
    fun junitXmlWith(tests: String, skipped: String): File =
        tempFolder.newFile("junit-$tests-$skipped.xml").apply {
            writeText(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <testsuite name="TestSuite" tests="$tests" skipped="$skipped" failures="0" errors="0">
                </testsuite>
                """.replaceIndent()
            )
        }
}
