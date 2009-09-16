package org.gradle.sample.impl

import org.gradle.sample.Person

/**
 * Immutable implementation of {@link Person}.
 */
class PersonImpl(val name: String) extends Person
{
    def getName() = name
}
