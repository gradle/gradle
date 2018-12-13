package samples;

import org.junit.Test;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;
import static samples.HelloWorld.greeting;

public class HelloWorldTest {

    @Test
    public void testGreeting() {
        assertThat(
            greeting(),
            equalTo("Hello, world!"));
    }
}
