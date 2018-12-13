package samples

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WriterTest {

    @Test
    fun `name normalization`() {
        val writer = Writer.builder()
                .name("")
                .age(33)
                .build()
        assertEquals("Anonymous", writer.name)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `age validation exception`() {
        Writer.builder()
                .name("Shakespeare")
                .age(-453)
                .build()
    }

    @Test
    fun `allow nullable age`() {
        val writer = Writer.builder()
                .name("Homer")
                .build()

        assertNull(writer.age)
    }

    @Test(expected = IllegalStateException::class)
    fun `builder without required value exception`() {
        Writer.builder().build()
    }

    @Test
    fun `empty list of books as default`() {
        val writer = Writer.builder()
                .name("Newbie")
                .age(18)
                .build()

        assertEquals(emptyList<String>(), writer.books)
    }
}
