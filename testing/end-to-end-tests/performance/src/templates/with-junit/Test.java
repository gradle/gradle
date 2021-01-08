package ${packageName};

import static org.junit.Assert.*;

public class ${testClassName} {

    private final ${productionClassName} production = new ${productionClassName}("value");

<% (binding.hasVariable("testMethodCount") ? testMethodCount : 20).times { index ->  %>
    @org.junit.Test
    public void test${index}() {
        assertEquals(production.getProperty(), "value");
    }
<% } %>
}
