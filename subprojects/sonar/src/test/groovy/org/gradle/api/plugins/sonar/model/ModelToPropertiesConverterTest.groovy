/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.plugins.sonar.model

import spock.lang.Specification

class ModelToPropertiesConverterTest extends Specification {
    class Model {
        @SonarProperty("my.prop")
        String annotated = "annotated"

        String notAnnotated = "notAnnotated"
    }

    def "converts model without annotations to empty map"() {
        def converter = new ModelToPropertiesConverter(new File("foo"))

        expect:
        converter.convert() == [:]
    }

    def "converts annotated properties"() {
        def converter = new ModelToPropertiesConverter(new Model())

        expect:
        converter.convert() == ["my.prop": "annotated"]
    }

    class Model2 {
        @SonarProperty("my.flag")
        boolean flag = false
    }

    def "converts property values to strings"() {
        def converter = new ModelToPropertiesConverter(new Model2())

        expect:
        converter.convert() == ["my.flag": "false"]
    }

    class Model22 {
        @SonarProperty("my.list")
        List<Integer> files = [1, 2, 3]
    }

    def "converts collection values to comma-separated strings"() {
        def converter = new ModelToPropertiesConverter(new Model22())

        expect:
        converter.convert() == ["my.list": "1,2,3"]
    }

    def "doesn't convert properties with null value"() {
        def model = new Model()
        model.annotated = null
        def converter = new ModelToPropertiesConverter(model)

        expect:
        converter.convert() == [:]
    }

    class Model3 {
        @IncludeProperties
        Model nested = new Model()

        Model2 nested2 = new Model2()
    }

    def "converts annotated nested models"() {
        def converter = new ModelToPropertiesConverter(new Model3())

        expect:
        converter.convert() == ["my.prop": "annotated"]
    }

    class Model4 extends Model {
        @SonarProperty("another.prop")
        int size = 4
    }

    def "handles inherited properties"() {
        def converter = new ModelToPropertiesConverter(new Model4())

        expect:
        converter.convert() == ["my.prop": "annotated", "another.prop": "4"]
    }

    class Model5 {
        @SonarProperty("one")
        String one = "one"


        @SonarProperty("two")
        String two = "two"
    }

    def "allows to post-process properties"() {
        def converter = new ModelToPropertiesConverter(new Model5())
        converter.propertyProcessors << { props ->
            props.put("added", "added")
        }
        converter.propertyProcessors << { props ->
            props.remove("one")
        }
        converter.propertyProcessors << { props ->
            props.two = "changed"
        }

        expect:
        converter.convert() == [added: "added", two: "changed"]
    }

    class Model6 {
        @SonarProperty("foo.bar")
        String prop = "prop"
    }

    def "allows to prefix property keys"() {
        def converter = new ModelToPropertiesConverter(new Model6(), "some.prefix")

        expect:
        converter.convert() == ["some.prefix.foo.bar": "prop"]
    }

    def "keys are only prefixed after property processors have run"() {
        def converter = new ModelToPropertiesConverter(new Model6(), "some.prefix")
        converter.propertyProcessors << { props ->
            assert props == ["foo.bar": "prop"]
        }

        expect:
        converter.convert() == ["some.prefix.foo.bar": "prop"]
    }

    def "non-string values added by property processors are converted"() {
        def converter = new ModelToPropertiesConverter(new Object())
        converter.propertyProcessors << { props ->
            props.other = [1, 2, 3]
        }

        expect:
        converter.convert() == [other: "1,2,3"]
    }

    class Model7 {
        @SonarProperty("prop")
        String prop
    }

    def "omits null values"() {
        def converter = new ModelToPropertiesConverter(new Model7())

        expect:
        converter.convert().isEmpty()
    }
}
