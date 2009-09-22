package org.gradle.api.tasks.compile

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
}

class TestOptions extends AbstractOptions {
    Integer intProp
    String stringProp
    Object objectProp
}