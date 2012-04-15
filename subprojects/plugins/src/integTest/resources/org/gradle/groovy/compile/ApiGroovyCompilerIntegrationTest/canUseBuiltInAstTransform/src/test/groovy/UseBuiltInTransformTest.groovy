import groovy.transform.ToString

import org.junit.Test

@ToString
class UseBuiltInTransformTest {
    String foo = "foo value"
    int bar = 42

    @Test
    void transformHasBeenApplied() {
        assert toString().contains("foo value")
        assert toString().contains("42")
    }
}
