package org.gradle.api.internal.tasks.testing.junit.report;

import org.junit.After;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.Locale;

import static junit.framework.Assert.assertEquals;

/**
 * @author Szczepan Faber, @date: 09.03.11
 */
public class LocaleSafeDecimalFormatTest {

    Locale defaultLocale = Locale.getDefault();

    @After
    public void revertLocalToDefault() {
        Locale.setDefault(defaultLocale);
    }

    @Test
    public void parsesCommaEvenInRareLocales() throws Exception {
        //given
        Locale usesCommaAsDecimalSeparator = new Locale("PL", "pl", "");
        Locale.setDefault(usesCommaAsDecimalSeparator);

        //when
        BigDecimal result = new LocaleSafeDecimalFormat().parse("1.05");

        //then
        assertEquals(1.05, result.doubleValue());
    }
}
