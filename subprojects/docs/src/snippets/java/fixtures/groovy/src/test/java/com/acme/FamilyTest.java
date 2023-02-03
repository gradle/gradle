package com.acme;

import org.junit.Test;
import org.apache.commons.lang3.tuple.ImmutablePair;
import static org.junit.Assert.*;
import static com.acme.Simpsons.*;

public class FamilyTest {
    @Test
    public void testFamily() {
        Family family = new Family(
            homer(),
            marge(),
            bart(),
            named("elisabeth marie"),
            of(ImmutablePair.of("Margaret Eve", "Simpson"))
        );
        System.out.println("family = " + family);
        System.out.println("maggie() = " + maggie());
        assertEquals(5, family.size());
        assertTrue(family.contains(lisa()));
        assertTrue(family.contains(maggie()));
    }
}
