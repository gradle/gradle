package ${packageName};

import org.testng.annotations.*;
import static org.testng.Assert.*;

public class ${testClassName} {
    private final ${productionClassName} production = new ${productionClassName}("value");

<% 20.times { index -> %>
    @Test
    public void test${index}() {
        assertEquals(production.getProperty(), "value");
    }
<% } %>
}
