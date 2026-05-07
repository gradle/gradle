package org.gradle;

import java.io.StringReader;
import org.junit.Test;
import static org.junit.Assert.*;
import org.antlr.runtime.CommonTokenStream;
import org.antlr.runtime.ANTLRStringStream;
public class GrammarTest {
    @Test
    public void canUseGeneratedGrammar() throws Exception {
        ANTLRStringStream in = new ANTLRStringStream("1+2");
        CalculatorLexer lexer = new CalculatorLexer(in);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        CalculatorParser parser = new CalculatorParser(tokens);
        parser.add();
    }
}
