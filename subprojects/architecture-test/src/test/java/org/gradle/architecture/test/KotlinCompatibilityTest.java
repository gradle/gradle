/*
 * Copyright 2018 the original author or authors.
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

import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multimap;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.base.PackageMatchers;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchUnitRunner;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import org.gradle.internal.reflect.PropertyAccessorType;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.lang.SimpleConditionEvent.violated;
import static com.tngtech.archunit.lang.conditions.ArchPredicates.are;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.partitioningBy;
import static java.util.stream.Collectors.toSet;

@RunWith(ArchUnitRunner.class)
@AnalyzeClasses(packages = "org.gradle")
public class KotlinCompatibilityTest {

    public static DescribedPredicate<JavaClass> gradlePublicApi() {
        return new GradlePublicApi();
    }

    private static final Multimap<String, String> IGNORED_PUBLIC_API_PROPERTIES = ImmutableSetMultimap.<String, String>builder()
        .put("org.gradle.StartParameter", "currentDir")
        .put("org.gradle.StartParameter", "gradleUserHomeDir")
        .put("org.gradle.StartParameter", "taskNames")
        .put("org.gradle.api.plugins.quality.Checkstyle", "configDir")
        .put("org.gradle.api.tasks.AbstractCopyTask", "dirMode")
        .put("org.gradle.api.tasks.AbstractCopyTask", "fileMode")
        .put("org.gradle.api.tasks.AbstractExecTask", "args")
        .put("org.gradle.api.tasks.AbstractExecTask", "executable")
        .put("org.gradle.api.tasks.JavaExec", "executable")
        .put("org.gradle.api.tasks.SourceSetOutput", "resourcesDir")
        .put("org.gradle.api.tasks.bundling.War", "classpath")
        .put("org.gradle.api.tasks.bundling.Zip", "metadataCharset")
        .put("org.gradle.api.tasks.compile.CompileOptions", "annotationProcessorGeneratedSourcesDirectory")
        .put("org.gradle.api.tasks.javadoc.Javadoc", "destinationDir")
        .put("org.gradle.api.tasks.javadoc.Javadoc", "maxMemory")
        .put("org.gradle.api.tasks.testing.Test", "forkEvery")
        .put("org.gradle.api.tasks.testing.testng.TestNGOptions", "parallel")
        .put("org.gradle.caching.http.HttpBuildCache", "url")
        .put("org.gradle.external.javadoc.MinimalJavadocOptions", "destinationDirectory")
        .put("org.gradle.ide.visualstudio.tasks.GenerateFiltersFileTask", "inputFile")
        .put("org.gradle.ide.visualstudio.tasks.GenerateProjectFileTask", "inputFile")
        .put("org.gradle.ide.visualstudio.tasks.GenerateSolutionFileTask", "inputFile")
        .put("org.gradle.ide.xcode.tasks.GenerateSchemeFileTask", "inputFile")
        .put("org.gradle.ide.xcode.tasks.GenerateXcodeWorkspaceFileTask", "inputFile")
        .put("org.gradle.plugin.devel.PluginDeclaration", "description")
        .put("org.gradle.plugin.devel.PluginDeclaration", "displayName")
        .put("org.gradle.plugins.ide.api.GeneratorTask", "inputFile")
        .put("org.gradle.plugins.ide.eclipse.model.ResourceFilterMatcher", "arguments")
        .put("org.gradle.plugins.ide.eclipse.model.ResourceFilterMatcher", "id")
        .put("org.gradle.testing.jacoco.plugins.JacocoTaskExtension", "destinationFile")
        .put("org.gradle.testing.jacoco.tasks.JacocoReportBase", "additionalClassDirs")
        .put("org.gradle.testing.jacoco.tasks.JacocoReportBase", "additionalSourceDirs")
        .put("org.gradle.workers.WorkerConfiguration", "displayName")
        .build();

    private static final Multimap<String, String> IGNORED_INTERNAL_API_PROPERTIES = ImmutableSetMultimap.<String, String>builder()
        .put("org.gradle.api.internal.artifacts.dependencies.AbstractModuleDependency", "targetConfiguration")
        .put("org.gradle.api.internal.artifacts.dependencies.DefaultMutableVersionConstraint", "branch")
        .put("org.gradle.api.internal.artifacts.ivyservice.modulecache.DefaultCachedMetadata", "processedMetadata")
        .put("org.gradle.api.internal.artifacts.ivyservice.modulecache.ModuleMetadataCache$CachedMetadata", "processedMetadata")
        .put("org.gradle.api.internal.artifacts.repositories.AbstractAuthenticationSupportedRepository", "configuredCredentials")
        .put("org.gradle.api.internal.artifacts.repositories.AuthenticationSupporter", "configuredCredentials")
        .put("org.gradle.api.internal.file.copy.CopySpecWrapper", "dirMode")
        .put("org.gradle.api.internal.file.copy.CopySpecWrapper", "fileMode")
        .put("org.gradle.api.internal.file.copy.DefaultCopySpec", "dirMode")
        .put("org.gradle.api.internal.file.copy.DefaultCopySpec", "fileMode")
        .put("org.gradle.api.internal.file.copy.DelegatingCopySpecInternal", "dirMode")
        .put("org.gradle.api.internal.file.copy.DelegatingCopySpecInternal", "fileMode")
        .put("org.gradle.api.internal.file.copy.SingleParentCopySpec", "dirMode")
        .put("org.gradle.api.internal.file.copy.SingleParentCopySpec", "fileMode")
        .put("org.gradle.api.internal.tasks.DefaultSourceSetOutput", "resourcesDir")
        .put("org.gradle.api.internal.tasks.TaskExecutionContext", "originExecutionMetadata")
        .put("org.gradle.api.internal.tasks.TaskExecutionContext", "upToDateMessages")
        .put("org.gradle.api.internal.tasks.TaskStateInternal", "outcome")
        .put("org.gradle.api.internal.tasks.execution.DefaultTaskExecutionContext", "upToDateMessages")
        .put("org.gradle.execution.plan.Node", "executionFailure")
        .put("org.gradle.ide.xcode.internal.xcodeproj.PBXObject", "globalID")
        .put("org.gradle.ide.xcode.internal.xcodeproj.PBXReference", "path")
        .put("org.gradle.ide.xcode.internal.xcodeproj.PBXShellScriptBuildPhase", "shellPath")
        .put("org.gradle.ide.xcode.internal.xcodeproj.PBXShellScriptBuildPhase", "shellScript")
        .put("org.gradle.ide.xcode.internal.xcodeproj.PBXTarget", "productName")
        .put("org.gradle.ide.xcode.internal.xcodeproj.PBXTarget", "productReference")
        .put("org.gradle.initialization.BuildLayoutParameters", "projectDir")
        .put("org.gradle.internal.artifacts.repositories.AuthenticationSupportedInternal", "configuredCredentials")
        .put("org.gradle.internal.component.external.model.ivy.DefaultMutableIvyModuleResolveMetadata", "branch")
        .put("org.gradle.plugins.ide.eclipse.model.internal.DefaultResourceFilterMatcher", "arguments")
        .put("org.gradle.plugins.ide.internal.tooling.model.LaunchableGradleTaskSelector", "description")
        .put("org.gradle.swiftpm.internal.DefaultTarget", "publicHeaderDir")
        .put("org.gradle.tooling.internal.gradle.ConsumerProvidedTaskSelector", "description")
        .build();

    @ArchTest
    public static final ArchRule consistent_nullable_annotations_on_public_api = classes().that(are(gradlePublicApi())).should(haveAccessorsWithSymmetricalNullableAnnotations(IGNORED_PUBLIC_API_PROPERTIES));

    @ArchTest
    public static final ArchRule consistent_nullable_annotations_on_internal_api = classes().that(are(not(gradlePublicApi()))).should(haveAccessorsWithSymmetricalNullableAnnotations(IGNORED_INTERNAL_API_PROPERTIES));

    private static ArchCondition<JavaClass> haveAccessorsWithSymmetricalNullableAnnotations(final Multimap<String, String> exclusions) {
        return new ArchCondition<JavaClass>("have accessors with symmetrical @Nullable annotations") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                findMutableProperties(item).stream()
                    .filter(accessors -> !accessors.nullableIsSymmetric())
                    .filter(accessors -> !exclusions.containsEntry(accessors.owningClass.getName(), accessors.propertyName))
                    .map(accessor -> violated(item, String.format("Accessors for %s don't use symmetrical @Nullable", accessor)))
                    .forEach(events::add);
            }

            private Set<Accessors> findMutableProperties(JavaClass javaClass) {
                Map<String, List<Accessor>> accessors = findAccessors(javaClass.getMethods());
                return accessors.entrySet().stream().map(entry -> Accessors.from(entry.getKey(), entry.getValue())).filter(Objects::nonNull).collect(toSet());
            }
        };
    }

    private static Map<String, List<Accessor>> findAccessors(Set<JavaMethod> methods) {
        return methods.stream().map(Accessor::from).filter(Objects::nonNull).collect(groupingBy(Accessor::getPropertyName));
    }

    private static class Accessor {
        private final PropertyAccessorType accessorType;
        private final JavaMethod method;
        private final boolean isGetter;

        @Nullable
        public static Accessor from(JavaMethod method) {
            PropertyAccessorType propertyAccessorType = PropertyAccessorType.fromName(method.getName());
            if (propertyAccessorType != null && (KotlinCompatibilityTest.isGetter(method, propertyAccessorType) || KotlinCompatibilityTest.isSetter(method, propertyAccessorType))) {
                return new Accessor(propertyAccessorType, method);
            }
            return null;
        }

        private Accessor(PropertyAccessorType accessorType, JavaMethod method) {
            this.accessorType = accessorType;
            this.method = method;
            this.isGetter = accessorType == PropertyAccessorType.IS_GETTER || accessorType == PropertyAccessorType.GET_GETTER;
        }

        public String getPropertyName() {
            return accessorType.propertyNameFor(method.getName());
        }

        public JavaMethod getMethod() {
            return method;
        }

        public boolean isGetter() {
            return isGetter;
        }

        public boolean isSetter() {
            return !isGetter;
        }
    }

    private static class Accessors {
        private final JavaClass owningClass;
        private final String propertyName;
        private final Set<JavaMethod> getters;
        private final Set<JavaMethod> setters;

        @Nullable
        public static Accessors from(String propertyName, List<Accessor> accessors) {
            Map<Boolean, List<Accessor>> gettersAndSetters = accessors.stream().collect(partitioningBy(Accessor::isGetter));
            JavaClass owningClass = accessors.iterator().next().getMethod().getOwner();
            Set<JavaMethod> getters = gettersAndSetters.get(Boolean.TRUE).stream().map(Accessor::getMethod).collect(toSet());
            Set<JavaMethod> setters = gettersAndSetters.get(Boolean.FALSE).stream().map(Accessor::getMethod).collect(toSet());
            if (!getters.isEmpty() && !setters.isEmpty()) {
                return new Accessors(owningClass, propertyName, getters, setters);
            }
            List<Accessor> superclassAccessors = findAccessors(owningClass.getAllMethods()).get(propertyName);
            if (superclassAccessors == null) {
                return null;
            }
            if (!setters.isEmpty()) {
                Set<JavaMethod> superclassGetters = superclassAccessors.stream().filter(Accessor::isGetter).map(Accessor::getMethod).collect(toSet());
                if (!superclassGetters.isEmpty()) {
                    return new Accessors(owningClass, propertyName, superclassGetters, setters);
                }
            } else {
                Set<JavaMethod> superclassSetters = superclassAccessors.stream().filter(Accessor::isSetter).map(Accessor::getMethod).collect(toSet());
                if (!superclassSetters.isEmpty()) {
                    return new Accessors(owningClass, propertyName, getters, superclassSetters);
                }
            }
            return null;
        }

        private Accessors(JavaClass owningClass, String propertyName, Set<JavaMethod> getters, Set<JavaMethod> setters) {
            this.getters = getters;
            this.setters = setters;
            this.owningClass = owningClass;
            this.propertyName = propertyName;
        }

        public boolean nullableIsSymmetric() {
            Set<Boolean> gettersAreNullable = getters.stream().map(this::getterAnnotatedWithNullable).collect(toSet());
            Set<Boolean> settersAreNullable = setters.stream().map(this::setterAnnotatedWithNullable).collect(toSet());
            if (gettersAreNullable.size() > 1 || settersAreNullable.size() > 1) {
                return false;
            }
            return gettersAreNullable.equals(settersAreNullable);
        }

        private boolean getterAnnotatedWithNullable(JavaMethod getter) {
            return getter.isAnnotatedWith(Nullable.class);
        }

        private boolean setterAnnotatedWithNullable(JavaMethod setter) {
            return Arrays.stream(setter.reflect().getParameterAnnotations()[0]).anyMatch(a -> a instanceof Nullable);
        }

        @Override
        public String toString() {
            return owningClass.getName() + "." + propertyName;
        }
    }

    private static boolean isGetter(JavaMethod m, PropertyAccessorType accessorType) {
        // We avoid using reflect, since that leads to class loading exceptions
        return !m.getModifiers().contains(JavaModifier.STATIC)
            && (accessorType == PropertyAccessorType.GET_GETTER || accessorType == PropertyAccessorType.IS_GETTER)
            && m.getRawParameterTypes().size() == 0
            && (accessorType != PropertyAccessorType.IS_GETTER || m.getRawReturnType().isEquivalentTo(Boolean.TYPE) || m.getRawReturnType().isEquivalentTo(Boolean.class));
    }

    private static boolean isSetter(JavaMethod m, PropertyAccessorType accessorType) {
        return !m.getModifiers().contains(JavaModifier.STATIC)
            && accessorType == PropertyAccessorType.SETTER
            && m.getRawParameterTypes().size() == 1;
    }

    private static class GradlePublicApi extends DescribedPredicate<JavaClass> {
        private static final PackageMatchers INCLUDES = PackageMatchers.of(parsePackageMatcher(System.getProperty("org.gradle.public.api.includes")));
        private static final PackageMatchers EXCLUDES = PackageMatchers.of(parsePackageMatcher(System.getProperty("org.gradle.public.api.excludes")));

        public GradlePublicApi() {
            super("Gradle public API");
        }

        @Override
        public boolean apply(JavaClass input) {
            return INCLUDES.apply(input.getPackageName()) && !EXCLUDES.apply(input.getPackageName());
        }

        private static Set<String> parsePackageMatcher(String packageList) {
            return Arrays.stream(packageList.split(":"))
                .map(include -> include.replace("**/", "..").replace("/**", "..").replace("/*", "").replace("/", "."))
                .collect(toSet());
        }
    }
}
