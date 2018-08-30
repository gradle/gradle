/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle;

import org.junit.Test;
import org.junit.Before;

import static org.junit.Assert.assertEquals;

public class PersonTest{

    Person person;
    @Before public void setup(){
        person = new Person();
    }

    @Test public void testAge() {
        person.setAge(30);
        assertEquals(30, person.getAge());
    }


    @Test public void testSurname() {
        person.setSurname("Duke");
        assertEquals("Duke", person.getSurname());
    }
}