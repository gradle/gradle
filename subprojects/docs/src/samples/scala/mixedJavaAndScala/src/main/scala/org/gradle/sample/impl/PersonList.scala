package org.gradle.sample.impl

import org.gradle.sample.Person

class PersonList {
    def find(name: String): Person = {
        new JavaPerson(name)
    }
}
