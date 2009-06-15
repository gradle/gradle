package org.gradle.api.tasks.compile

import org.gradle.api.tasks.compile.AbstractOptions
import org.junit.Test
import static org.gradle.util.Matchers.*
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*

public class AbstractOptionsTest {
    private final TestOptions options = new TestOptions()

    @Test
    public void hasEmptyOptionsMapWhenEverythingIsNull() {
        assertThat(options.optionMap(), isEmptyMap())
    }

    @Test
    public void optionsMapIncludesNonNullValues() {
        assertThat(options.optionMap(), isEmptyMap())

        options.intProp = 9
        Map expected = new LinkedHashMap();
        expected.intProp = 9
        assertThat(options.optionMap(), equalTo(expected))

        options.stringProp = 'string'
        expected.stringProp = 'string'
        assertThat(options.optionMap(), equalTo(expected))
    }

    @Test
    public void hasEmptyQuotedOptionsMapWhenEverythingIsNull() {
        assertThat(options.quotedOptionMap(), isEmptyMap())
        assertThat("${options.quotedOptionMap()}" as String, equalTo('[:]'))
    }

    @Test
    public void quotedOptionsMapIncludesNonNullValues() {
        assertThat(options.quotedOptionMap(), isEmptyMap())

        options.intProp = 9
        Map expected = new LinkedHashMap();
        expected.intProp = '9'
        assertThat(options.quotedOptionMap(), equalTo(expected))

        options.stringProp = 'string'
        expected.stringProp = '\'string\''
        assertThat(options.quotedOptionMap(), equalTo(expected))
        assertThat("${options.quotedOptionMap()}" as String, equalTo("[intProp:9, stringProp:'string']"))

        options.objectProp = "string"
        expected.objectProp = '\'string\''
        assertThat(options.quotedOptionMap(), equalTo(expected))
    }

    @Test
    public void escapesCharsInStringValues() {
        options.stringProp = '\'\\\n'
        assertThat("${options.quotedOptionMap()}" as String, equalTo("[stringProp:'\\'\\\\\n']"))
    }
}

class TestOptions extends AbstractOptions {
    Integer intProp
    String stringProp
    Object objectProp
}