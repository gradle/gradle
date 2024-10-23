package ${packageName};

import static org.junit.Assert.*;

public class ${testClassName} {

    private final ${productionClassName} production = new ${productionClassName}("value");

    @org.junit.Test
    public void testOne() {
        for (int i = 0; i < 1000; i++) {
            System.out.println("Some test output from ${testClassName}.testOne - " + i);
            System.err.println("Some test error  from ${testClassName}.testOne - " + i);
        }
        assertEquals(production.getProperty(), "value");
    }

    @org.junit.Test
    public void testTwo() {
        for (int i = 0; i < 1000; i++) {
            System.out.println("Some test output from ${testClassName}.testTwo - " + i);
            System.err.println("Some test error  from ${testClassName}.testTwo - " + i);
        }
        String expected = <%= binding.hasVariable("halfTestsFail") && binding.halfTestsFail ? "\"foo\"" : "\"value\"" %>;
        assertEquals(production.getProperty(), expected);
    }
}
