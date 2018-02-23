package org.gradle;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

@RunWith(org.gradle.Locales.class)
public class SomeLocaleTests {
    @Test
    public void ok1() {
        System.out.println("Locale in use: " + LocaleHolder.get());
    }

    @Test
    @Category(org.gradle.CategoryA.class)
    public void ok2() {
        System.out.println("Locale in use: " + LocaleHolder.get());
    }
}