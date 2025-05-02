package org.gradle;

import org.apache.commons.lang.StringUtils;

/**
 * <p>Represents a person.</p>
 */
public class Person {
    private final String name;

    public Person(String name) {
        if (StringUtils.isEmpty(name)) {
            throw new IllegalArgumentException();
        }
        this.name = name;
    }

    /**
     * Get the person's name
     *
     * @return the name
     */
    public String getName() {
        return name;
    }
}
