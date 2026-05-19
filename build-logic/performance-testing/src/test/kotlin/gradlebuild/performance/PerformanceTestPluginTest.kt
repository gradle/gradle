package gradlebuild.performance

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File


class PerformanceTestPluginTest {

    @TempDir
    lateinit var tempFolder: File

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
        File(tempFolder, "junit-$tests-$skipped.xml").apply {
            writeText(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <testsuite name="TestSuite" tests="$tests" skipped="$skipped" failures="0" errors="0">
                </testsuite>
                """.replaceIndent()
            )
        }
}
