/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.internal.resolve

import org.gradle.api.Named
import org.gradle.language.base.internal.model.DefaultVariantDimensionSelectorFactory
import org.gradle.language.base.internal.model.DefaultVariantsMetaData
import org.gradle.language.base.internal.model.VariantDimensionSelector
import org.gradle.model.internal.manage.schema.extract.DefaultModelSchemaStore
import org.gradle.model.internal.manage.schema.extract.ModelSchemaAspectExtractor
import org.gradle.model.internal.manage.schema.extract.ModelSchemaExtractor
import org.gradle.platform.base.Platform
import org.gradle.platform.base.Variant
import org.gradle.platform.base.internal.BinarySpecInternal
import org.gradle.platform.base.internal.VariantAspectExtractionStrategy
import spock.lang.Specification
import spock.lang.Unroll

class VariantsMatcherTest extends Specification {
    def schemaStore = new DefaultModelSchemaStore(new ModelSchemaExtractor([], new ModelSchemaAspectExtractor([new VariantAspectExtractionStrategy()])))

    @Unroll
    def "should filter binaries based on requirements"() {
        given: "a library binary with some requirements"

        def factories = [DefaultVariantDimensionSelectorFactory.of(MyPlatform, new MySelector())]
        def matcher = new VariantsMatcher(factories, CustomSpecBase, schemaStore)
        def reference = DefaultVariantsMetaData.extractFrom(spec, schemaStore)

        when: "we filter binaries based on requirements"
        def filtered = matcher.filterBinaries(reference, binaries)

        then: "only the expected binaries are returned"
        filtered as Set == expected as Set

        where:
        spec                                | binaries                                                                                                                                      | expected
        refSpec()                           | []                                                                                                                                            | []
        refSpec()                           | [nullPlatform()]                                                                                                                        | []
        refSpec()                           | [refSpec()]                                                                                                                                   | [refSpec()]
        refSpec('1.7')                      | [refSpec('1.6')]                                                                                                                              | [refSpec('1.6')]
        refSpec('1.6')                      | [refSpec('1.7')]                                                                                                                              | []
        refSpec('1.6')                      | [refSpec('1.5'), refSpec('1.6'), refSpec('1.7')]                                                                                              | [refSpec('1.6')]
        refSpec()                           | [customSpec1()]                                                                                                                               | [customSpec1()]
        customSpec1()                       | [customSpec1()]                                                                                                                               | [customSpec1()]
        refSpec()                           | [customSpec1(), customSpec1('1.7', 'paid')]                                                                                                   | [customSpec1(), customSpec1('1.7', 'paid')]
        customSpec1()                       | [customSpec1(), customSpec1('1.7', 'paid')]                                                                                                   | [customSpec1()]
        customSpec1('1.7', null)            | [customSpec1(), customSpec1('1.7', 'paid')]                                                                                                   | [customSpec1(), customSpec1('1.7', 'paid')]
        customSpec1('1.7', 'paid')          | [customSpec1(), customSpec1('1.7', 'paid')]                                                                                                   | [customSpec1('1.7', 'paid')]
        customSpec1('1.6', 'paid')          | [customSpec1(), customSpec1('1.7', 'paid'), customSpec1('1.6', 'paid')]                                                                       | [customSpec1('1.6', 'paid')]
        customSpec1('1.7', 'paid')          | [customSpec1(), customSpec1('1.7', 'paid'), customSpec1('1.6', 'paid')]                                                                       | [customSpec1('1.7', 'paid')]
        customSpec1('1.7', 'paid')          | [customSpec2(), customSpec1('1.6', 'free'), customSpec1('1.7', 'paid'), customSpec1('1.6', 'paid')]                                           | [customSpec1('1.7', 'paid')]
        customSpec1()                       | [customSpec2()]                                                                                                                               | [customSpec2()]
        customSpec2('1.7', null, null)      | [refSpec()]                                                                                                                                   | [refSpec()]
        customSpec2('1.7', 'free', null)    | [customSpec1()]                                                                                                                               | [customSpec1()]
        customSpec2()                       | [customSpec2()]                                                                                                                               | [customSpec2()]
        customSpec2('1.7', 'paid', 'debug') | [customSpec2()]                                                                                                                               | []
        customSpec2('1.7', 'paid', 'debug') | [customSpec2('1.7', null, 'debug')]                                                                                                           | [customSpec2('1.7', null, 'debug')]
        customSpec2('1.7', 'paid', 'debug') | [customSpec2('1.6', 'paid', null), customSpec2('1.6', null, 'debug')]                                                                         | [customSpec2('1.6', 'paid', null), customSpec2('1.6', null, 'debug')]
        customSpec2('1.7', 'paid', 'debug') | [customSpec2('1.6', 'paid', null), customSpec2('1.6', null, 'debug'), customSpec2('1.7', null, null)]                                         | [customSpec2('1.7', null, null)]
        customSpec2('1.7', 'paid', 'debug') | [customSpec2('1.6', 'paid', null), customSpec2('1.6', null, 'debug'), customSpec2('1.7', null, 'debug'), customSpec2('1.7', 'paid', 'debug')] | [customSpec2('1.7', null, 'debug'), customSpec2('1.7', 'paid', 'debug')]
        customSpec2()                       | [customSpec3()]                                                                                                                               | [customSpec3()]
        customSpec3()                       | [customSpec2()]                                                                                                                               | []
        customSpec2('1.6', 'paid', 'debug') | [customSpec2('1.6', 'paid', 'debug'), customSpec3('1.6', 'paid', 'debug', 'bar')]                                                             | [customSpec2('1.6', 'paid', 'debug'), customSpec3('1.6', 'paid', 'debug', 'bar')]
        customSpec2('1.6', 'paid', 'debug') | [customSpec3('1.6', 'paid', 'debug', 'foo'), customSpec3('1.6', 'paid', 'debug', 'bar')]                                                      | [customSpec3('1.6', 'paid', 'debug', 'foo'), customSpec3('1.6', 'paid', 'debug', 'bar')]
    }

    @Unroll
    def "can use a custom variant comparator"() {
        def factories = [
            DefaultVariantDimensionSelectorFactory.of(MyPlatform, new MySelector()),
            DefaultVariantDimensionSelectorFactory.of(BuildType, new VariantDimensionSelector<BuildType>() {
                @Override
                boolean isCompatibleWithRequirement(BuildType requirement, BuildType value) {
                    requirement.name.length() == value.name.length()
                }

                @Override
                boolean betterFit(BuildType requirement, BuildType first, BuildType second) {
                    second.name == requirement.name
                }
            })
        ]
        def matcher = new VariantsMatcher(factories, CustomSpecBase, schemaStore)
        def reference = DefaultVariantsMetaData.extractFrom(spec, schemaStore)

        when: "we filter binaries based on requirements"
        def filtered = matcher.filterBinaries(reference, binaries)

        then: "only the expected binaries are returned"
        filtered as Set == expected as Set

        where:
        spec                              | binaries                                                               | expected
        customSpec2()                     | [customSpec2()]                                                        | [customSpec2()]
        customSpec2('1.7', 'paid', 'XXX') | [customSpec2('1.7', 'paid', 'XXX')]                                    | [customSpec2('1.7', 'paid', 'XXX')]
        customSpec2('1.7', 'paid', 'XXX') | [customSpec2('1.7', 'paid', 'YYY')]                                    | [customSpec2('1.7', 'paid', 'YYY')]
        customSpec2('1.7', 'paid', 'XXX') | [customSpec2('1.7', 'paid', 'YYYY')]                                   | []
        customSpec2('1.7', 'paid', null)  | [customSpec2('1.7', 'paid', 'YYYY')]                                   | [customSpec2('1.7', 'paid', 'YYYY')]
        customSpec2('1.7', 'paid', 'XXX') | [customSpec2('1.7', 'paid', null)]                                     | [customSpec2('1.7', 'paid', null)]
        customSpec2('1.7', 'paid', 'XXX') | [customSpec2('1.7', 'paid', 'XXX'), customSpec2('1.7', 'paid', 'YYY')] | [customSpec2('1.7', 'paid', 'XXX')]
        customSpec2('1.7', 'paid', 'XXX') | [customSpec2('1.7', 'paid', 'ZZZ'), customSpec2('1.7', 'paid', 'YYY')] | [customSpec2('1.7', 'paid', 'YYY'), customSpec2('1.7', 'paid', 'ZZZ')]

    }

    private CustomSpecBase nullPlatform() {
        def spec = Mock(CustomSpecBase)
        spec.publicType >> CustomSpecBase
        spec
    }

    private CustomSpecBase refSpec(String version = '1.7') {
        def spec = Mock(CustomSpecBase)
        spec.publicType >> CustomSpecBase
        spec.targetPlatform >> new MyPlatform(name:version)
        spec.displayName >> { "CustomBinarySpec ($spec.targetPlatform)" }
        spec.equals(_) >> { args -> spec.displayName == args[0].displayName }
        spec.hashCode() >> { spec.displayName.hashCode() }
        spec.toString() >> { spec.displayName }
        spec
    }

    private CustomSpec1 customSpec1(String version = '1.7', String flavor = 'free') {
        def spec = Mock(CustomSpec1)
        spec.publicType >> CustomSpec1
        spec.targetPlatform >> new MyPlatform(name:version)
        spec.flavor >> flavor
        spec.displayName >> { "CustomSpec1 ($version, $flavor)" }
        spec.equals(_) >> { args -> spec.displayName == args[0].displayName }
        spec.hashCode() >> { spec.displayName.hashCode() }
        spec.toString() >> { spec.displayName }
        spec
    }

    private CustomSpec2 customSpec2(String version = '1.7', String flavor = 'free', String buildType = 'release') {
        def spec = Mock(CustomSpec2)
        spec.publicType >> CustomSpec2
        spec.targetPlatform >> new MyPlatform(name:version)
        spec.flavor >> flavor
        spec.buildType >> { buildType ? new BuildType(name: buildType) : null }
        spec.displayName >> { "CustomSpec2 ($version, $flavor, $buildType)" }
        spec.equals(_) >> { args -> spec.displayName == args[0].displayName }
        spec.hashCode() >> { spec.displayName.hashCode() }
        spec.toString() >> { spec.displayName }
        spec
    }

    private CustomSpec3 customSpec3(String version = '1.7', String flavor = 'free', String buildType = 'release', String customValue = 'foo') {
        def spec = Mock(CustomSpec3)
        spec.publicType >> CustomSpec3
        spec.targetPlatform >> new MyPlatform(name:version)
        spec.flavor >> flavor
        spec.buildType >> { buildType ? new BuildType2(name: buildType, customValue: customValue) : null }
        spec.displayName >> { "CustomSpec3 ($version, $flavor, $buildType, $customValue)" }
        spec.equals(_) >> { args -> spec.displayName == args[0].displayName }
        spec.hashCode() >> { spec.displayName.hashCode() }
        spec.toString() >> { spec.displayName }
        spec
    }

    static interface CustomSpecBase extends BinarySpecInternal {
        @Variant
        Platform getTargetPlatform()
    }

    static interface CustomSpec1 extends CustomSpecBase {
        @Variant
        String getFlavor()
    }

    static interface CustomSpec2 extends CustomSpecBase {
        @Variant
        String getFlavor()

        @Variant
        BuildType getBuildType()
    }

    static interface CustomSpec3 extends CustomSpecBase {
        @Variant
        String getFlavor()

        @Variant
        BuildType2 getBuildType()
    }

    static class BuildType implements Named {
        String name
    }

    static class BuildType2 extends BuildType {
        String customValue
    }

    static class MyPlatform implements Platform {
        String name

        @Override
        String getDisplayName() {
            "Java $name"
        }

        @Override
        String getName() {
            name
        }


        @Override
        public String toString() {
            displayName
        }
    }

    static class MySelector implements VariantDimensionSelector<MyPlatform> {
        private static int v(MyPlatform platform) {
            Integer.valueOf(platform.name.replaceAll(/\./,''))
        }

        @Override
        boolean isCompatibleWithRequirement(MyPlatform requirement, MyPlatform value) {
            v(requirement) >= v(value)
        }

        @Override
        boolean betterFit(MyPlatform requirement, MyPlatform first, MyPlatform second) {
            v(first) < v(second)
        }
    }
}
