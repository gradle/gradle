package org.gradle.api.internal.tasks.testing.junit.report;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParseException;

/**
 * @author Szczepan Faber, @date: 09.03.11
 */
public class LocaleSafeDecimalFormat {

    /**
     * Regardless of the default locale, comma ('.') is used as decimal separator
     *
     * @param source
     * @return
     * @throws ParseException
     */
    public BigDecimal parse(String source) throws ParseException {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setDecimalSeparator('.');
        DecimalFormat format = new DecimalFormat("#.#", symbols);
        format.setParseBigDecimal(true);
        return (BigDecimal) format.parse(source);
    }
}
