package org.gradle.sample.impl

import org.gradle.sample.api.Person

/**
 * Immutable implementation of {@link Person}.
 */
class PersonImpl(val names: List[String]) extends Person
{
}
