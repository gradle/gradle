package ${packageName};

import static org.junit.Assert.*;

public class ${testClassName} {
    private final ${productionClassName} production = new ${productionClassName}("value");

    @org.junit.Test
    public void test() {
        assertEquals(production.getProperty(), "value");
    }
}