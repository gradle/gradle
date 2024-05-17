/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.architecture.test;

import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.gradle.internal.xml.XmlFactories;

import javax.xml.datatype.DatatypeFactory;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.validation.SchemaFactory;
import javax.xml.xpath.XPathFactory;
import java.util.Arrays;
import java.util.List;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.base.DescribedPredicate.doNot;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.belongToAnyOf;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "org.gradle")
public class XmlFactoriesTest {

    private static final String RATIONALE = "for security reasons, all XML factories creation should go through " + XmlFactories.class.getName();

    private static final List<Class<?>> xmlFactoryClasses = Arrays.asList(
        DocumentBuilderFactory.class,
        SAXParserFactory.class,
        TransformerFactory.class,
        XPathFactory.class,
        SchemaFactory.class,
        XMLEventFactory.class,
        XMLInputFactory.class,
        XMLOutputFactory.class,
        DatatypeFactory.class
    );

    @ArchTest
    @SuppressWarnings("unused")
    public static final ArchRule no_xml_factories =
        noClasses()
            .that(doNot(belongToAnyOf(XmlFactories.class)))
            .should()
            .callMethodWhere(describe("static XML factories", methodCall ->
                xmlFactoryClasses.stream().anyMatch(clazz -> methodCall.getTarget().getOwner().isAssignableFrom(clazz)) &&
                    methodCall.getTarget().resolveMember().get().getModifiers().contains(JavaModifier.STATIC)
            ))
            .because(RATIONALE);
}
