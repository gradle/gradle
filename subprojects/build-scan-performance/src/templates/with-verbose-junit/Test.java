package ${packageName};

import static org.junit.Assert.*;

public class ${testClassName} {

    private final ${productionClassName} production = new ${productionClassName}("value");

    @org.junit.Test
    public void testOne() throws Exception {
        for (int i = 0; i < 500; i++) {
            System.out.println("Some test output from ${testClassName}.testOne - " + i);
            System.err.println("Some test error  from ${testClassName}.testOne - " + i);
            if (i % 50 == 0) {
                Thread.sleep(5);
            }
        }
        assertEquals(production.getProperty(), "value");
    }

    @org.junit.Test
    public void testTwo() throws Exception {
        for (int i = 0; i < 500; i++) {
            System.out.println("Some test output from ${testClassName}.testTwo - " + i);
            System.err.println("Some test error  from ${testClassName}.testTwo - " + i);
            if (i % 50 == 0) {
                Thread.sleep(5);
            }
        }
        String expected = <%= binding.hasVariable("halfTestsFail") && binding.halfTestsFail ? "\"foo\"" : "\"value\"" %>;
        assertEquals(production.getProperty(), expected);
    }

    @org.junit.Test
    public void testThree() throws Exception {
        assertEquals(production.getProperty(), "value");
    }

    @org.junit.Test
    public void testFour() throws Exception {
        assertEquals(production.getProperty(), "value");
        }
}
