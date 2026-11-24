/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.serialize

import spock.lang.Specification


class UnsafeDeserializationClassesTest extends Specification {

    def "refuses known gadget sinks"() {
        when:
        UnsafeDeserializationClasses.checkNotBlocked(className)

        then:
        def e = thrown(InvalidClassException)
        e.classname == className

        where:
        className << [
            "com.sun.org.apache.xalan.internal.xsltc.trax.TemplatesImpl",
            "com.sun.org.apache.xpath.internal.objects.XString",
            "com.sun.rowset.JdbcRowSetImpl",
            "org.apache.commons.collections.functors.InvokerTransformer",
            "org.apache.commons.collections4.functors.InvokerTransformer",
            "org.apache.commons.beanutils.BeanComparator",
            "java.security.SignedObject",
            "sun.reflect.annotation.AnnotationInvocationHandler",
        ]
    }

    def "allows classes that Gradle legitimately serializes"() {
        when:
        UnsafeDeserializationClasses.checkNotBlocked(className)

        then:
        noExceptionThrown()

        where:
        className << [
            "java.lang.String",
            "java.util.HashMap",
            "java.lang.RuntimeException",
            "org.gradle.api.GradleException",
            // shares a prefix root with a blocked package but is not itself blocked
            "com.sun.rowset",
            "org.apache.commons.collections.list.GrowthList",
        ]
    }
}
