package org.gradle;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@Category(CategoryA.class)
public class SomeTest {

    @Test
    public void testOk1() {
    }

    @Test
    @Category(CategoryC.class)
    public void testOk2() {
    }

    @Test
    @Category(CategoryB.class)
    public void testOk3() {
    }

    @Test
    @Category({CategoryB.class, CategoryC.class})
    public void testOk4() {
    }
}
