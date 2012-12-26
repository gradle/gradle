package ${packageName};

import org.testng.annotations.*;
import static org.testng.Assert.*;

public class ${testClassName} {
    private final ${productionClassName} production = new ${productionClassName}("value");

    @Test
    public void testOne() {
        assertEquals(production.getProperty(), "value");
    }
}
