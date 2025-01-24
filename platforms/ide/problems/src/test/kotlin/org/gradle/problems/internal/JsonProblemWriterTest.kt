import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import groovy.json.JsonSlurper
import org.gradle.api.problems.FileLocation
import org.gradle.api.problems.LineInFileLocation
import org.gradle.api.problems.ProblemDefinition
import org.gradle.api.problems.Severity
import org.gradle.api.problems.internal.DefaultProblem
import org.gradle.api.problems.internal.DefaultProblemGroup
import org.gradle.api.problems.internal.DefaultProblemId
import org.gradle.internal.cc.impl.problems.JsonWriter
import org.gradle.internal.configuration.problems.FailureDecorator
import org.gradle.internal.extensions.stdlib.uncheckedCast
import org.gradle.internal.problems.failure.FailureFactory
import org.gradle.problems.internal.impl.JsonProblemWriter
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringWriter

class JsonProblemWriterTest {

    @Test
    fun `JsonProblemWriter includes contextualLocations and originLocations in JSON`() {
        // Mock locations
        val originLocation = mock<FileLocation> {
            on { path } doReturn "/path/to/file1"
        }
        val contextualLocation = mock<LineInFileLocation> {
            on { path } doReturn "/path/to/file2"
            on { line } doReturn 42
            on { column } doReturn 5
            on { length } doReturn 10
        }

        val def = mock<ProblemDefinition> {
            on { id } doReturn DefaultProblemId("id", "displayName", DefaultProblemGroup("groupId", "groupDisplayName"))
            on { severity } doReturn Severity.WARNING
        }
        val problem = DefaultProblem(def, "context", listOf(), listOf(originLocation), listOf(contextualLocation), "details", null, null)

        // Mock dependencies
        val failureDecorator = FailureDecorator()
        val failureFactory = mock<FailureFactory>()
        val stringWriter = StringWriter()
        val jsonWriter = JsonWriter(stringWriter)

        // Write JSON using JsonProblemWriter
        val jsonProblemWriter = JsonProblemWriter(problem, failureDecorator, failureFactory)
        jsonProblemWriter.writeToJson(jsonWriter)

        jsonWriter.flush()
        val toString = stringWriter.toString()
        val jsonMap = JsonSlurper().parseText(toString).uncheckedCast() as Map<String, Any>

        assertTrue((jsonMap.get("locations")?.uncheckedCast() as List<Map<Any, Any>>).size == 2)
    }
}
