package org.gradle

class PersonList {
    def find(String name) {
        new JavaPerson(name)
    }
}
