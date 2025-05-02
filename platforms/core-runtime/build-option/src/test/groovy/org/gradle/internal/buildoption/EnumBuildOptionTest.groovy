/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.buildoption

import spock.lang.Specification

class EnumBuildOptionTest extends Specification {
    def option = new MyEnumBuildOption()

    def "reasonable error message when user doesn't select a correct value"() {
        def receiver = Mock(Dummy)

        when:
        option.applyFromProperty([test: 'thou'], receiver)

        then:
        1 * receiver.accept(MyEnum.THOU)

        when:
        option.applyFromProperty([test: 'SHALT'], receiver)

        then:
        1 * receiver.accept(MyEnum.SHALT)

        when:
        option.applyFromProperty([test: 'nOt'], receiver)

        then:
        1 * receiver.accept(MyEnum.NOT)

        when:
        option.applyFromProperty([test: 'pazz'], receiver)

        then:
        RuntimeException ex = thrown()
        ex.message == "Option my option doesn't accept value 'pazz'. Possible values are [THOU, SHALT, NOT, PASS]"
    }

    interface Dummy {
        void accept(MyEnum value)
    }

    static class MyEnumBuildOption extends EnumBuildOption<MyEnum, Dummy> {

        MyEnumBuildOption() {
            super("my option", MyEnum, MyEnum.values(), "test")
        }

        @Override
        void applyTo(MyEnum value, Dummy settings, Origin origin) {
            settings.accept(value)
        }
    }

    enum MyEnum {
        THOU,
        SHALT,
        NOT,
        PASS
    }
}
