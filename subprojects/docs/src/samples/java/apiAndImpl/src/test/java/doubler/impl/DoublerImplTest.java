package doubler.impl;

import doubler.Doubler;
import org.junit.Test;

public class DoublerImplTest {
    
    @Test
    public void testIt() {
        Doubler doubler = new DoublerImpl();
        assert doubler.doubleIt(2) == 4;
    }
}