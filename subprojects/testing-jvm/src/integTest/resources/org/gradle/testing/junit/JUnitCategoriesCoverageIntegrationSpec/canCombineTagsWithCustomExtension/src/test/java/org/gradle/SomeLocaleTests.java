package org.gradle;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Locale;

@ExtendWith(org.gradle.Locales.class)
public class SomeLocaleTests {
    @TestTemplate
    public void ok1(Locale locale) {
        System.out.println("Locale in use: " + locale);
    }

    @TestTemplate
    @Tag("org.gradle.CategoryA")
    public void ok2(Locale locale) {
        System.out.println("Locale in use: " + locale);
    }
}
