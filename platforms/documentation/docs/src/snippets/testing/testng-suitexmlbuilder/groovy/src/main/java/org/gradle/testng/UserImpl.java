package org.gradle.testng;

public class UserImpl implements User
{
    private final String firstName;
    private final String lastName;

    public UserImpl(String firstName, String lastName)
    {
        this.firstName = firstName;
        this.lastName = lastName;
    }

    public String getFirstName()
    {
        return firstName;
    }

    public String getLastName()
    {
        return lastName;
    }
}
