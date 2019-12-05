package org.gradle.testng;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.testng.annotations.AfterMethod;
import static org.testng.Assert.*;

public class UserImplTest
{
    private UserImpl user;

    private final String okFirstName = "Tom";
    private final String okLastName = "Eyckmans";

    @BeforeMethod
    public void setUp() throws Exception
    {
        user = new UserImpl(okFirstName, okLastName);
    }

    @Test
    public void testOkFirstName()
    {
        assertEquals(user.getFirstName(), okFirstName);
    }

    @Test
    public void testOkLastName()
    {
        assertEquals(user.getLastName(), okLastName);
    }

    @AfterMethod
    public void tearDown() throws Exception
    {
        user = null;
    }
}
