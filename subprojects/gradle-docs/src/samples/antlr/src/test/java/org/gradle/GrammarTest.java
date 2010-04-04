package org.gradle;

import java.io.StringReader;
import org.junit.Test;
import static org.junit.Assert.*;

public class GrammarTest {
    @Test
    public void canUseGeneratedGrammar() throws Exception {
        CalculatorLexer lexer = new CalculatorLexer(new StringReader("1+2"));
        CalculatorParser parser = new CalculatorParser(lexer);
        parser.add();
    }
}
