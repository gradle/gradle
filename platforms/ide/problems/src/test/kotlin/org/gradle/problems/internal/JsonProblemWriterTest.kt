import groovy.json.JsonSlurper
import org.gradle.api.problems.FileLocation
import org.gradle.api.problems.LineInFileLocation
import org.gradle.api.problems.ProblemDefinition
import org.gradle.api.problems.Severity
import org.gradle.api.problems.internal.DefaultProblem
import org.gradle.api.problems.internal.DefaultProblemGroup
import org.gradle.api.problems.internal.DefaultProblemId
import org.gradle.api.problems.internal.StackTraceLocation
import org.gradle.internal.cc.impl.problems.JsonWriter
import org.gradle.internal.configuration.problems.FailureDecorator
import org.gradle.internal.extensions.stdlib.uncheckedCast
import org.gradle.internal.problems.failure.FailureFactory
import org.gradle.problems.internal.impl.JsonProblemWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
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
        val stackLocationFileLocation = mock<FileLocation> {
            on { path } doReturn "/path/to/stackfile"
        }
        val stackTraceLocation = mock<StackTraceLocation> {
            on { fileLocation } doReturn stackLocationFileLocation
        }

        // Setup problem definition and instance
        val problemDefinition = mock<ProblemDefinition> {
            on { id } doReturn DefaultProblemId("id", "displayName", DefaultProblemGroup("groupId", "groupDisplayName"))
            on { severity } doReturn Severity.WARNING
        }
        val problem = DefaultProblem(
            problemDefinition,
            "context",
            listOf(),
            listOf(originLocation, stackTraceLocation),
            listOf(contextualLocation),
            "details",
            null,
            null
        )

        // Setup JSON writing dependencies
        val failureDecorator = FailureDecorator()
        val failureFactory = mock<FailureFactory>()
        val stringWriter = StringWriter()
        val jsonWriter = JsonWriter(stringWriter)

        // Act: Write JSON
        val jsonProblemWriter = JsonProblemWriter(problem, failureDecorator, failureFactory)
        jsonProblemWriter.writeToJson(jsonWriter)
        jsonWriter.flush()
        val jsonText = stringWriter.toString()
        val jsonMap = JsonSlurper().parseText(jsonText).uncheckedCast() as Map<String, Any>

        // Assert: All locations are present
        val locations = jsonMap["locations"]?.uncheckedCast<List<Any>>()
        assertNotNull(locations)
        assertEquals(3, locations?.size)
    }
}
