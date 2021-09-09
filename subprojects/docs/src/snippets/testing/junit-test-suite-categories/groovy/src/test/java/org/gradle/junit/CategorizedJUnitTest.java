package org.gradle.junit;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class CategorizedJUnitTest {

    @Category(CategoryA.class)
    @Test
    public void a() {
        System.out.println("hello from CategorizedTest a.");
    }

    @Category(CategoryB.class)
    @Test
    public void b() {
        System.out.println("hello from CategorizedTest b.");
    }
}
