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

package org.gradle.api.problems.internal;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.Action;
import org.gradle.api.problems.AdditionalData;
import org.gradle.api.problems.DocLink;
import org.gradle.api.problems.FileLocation;
import org.gradle.api.problems.ProblemDefinition;
import org.gradle.api.problems.ProblemGroup;
import org.gradle.api.problems.ProblemId;
import org.gradle.api.problems.ProblemLocation;
import org.gradle.api.problems.Severity;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.problems.Location;
import org.gradle.problems.ProblemDiagnostics;
import org.gradle.problems.buildtree.ProblemStream;
import org.gradle.tooling.internal.provider.serialization.PayloadSerializer;
import org.gradle.tooling.internal.provider.serialization.SerializedPayload;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DefaultProblemBuilder implements InternalProblemBuilder {
    @Nullable
    private ProblemStream problemStream;

    private ProblemId id;
    private String contextualLabel;
    private Severity severity;
    private final List<ProblemLocation> locations = new ArrayList<ProblemLocation>();
    private final List<ProblemLocation> contextLocations = new ArrayList<ProblemLocation>();
    private String details;
    private DocLink docLink;
    private List<String> solutions;
    private Throwable exception;
    //TODO Reinhold make private again
    private AdditionalData additionalData;
    private boolean collectLocation = false;
    private final AdditionalDataBuilderFactory additionalDataBuilderFactory;
    private final Instantiator instantiator;
    private final PayloadSerializer payloadSerializer;

    public DefaultProblemBuilder(AdditionalDataBuilderFactory additionalDataBuilderFactory, Instantiator instantiator, PayloadSerializer payloadSerializer) {
        this.additionalDataBuilderFactory = additionalDataBuilderFactory;
        this.instantiator = instantiator;
        this.payloadSerializer = payloadSerializer;
        this.additionalData = null;
        this.solutions = new ArrayList<String>();
    }

    public DefaultProblemBuilder(@Nullable ProblemStream problemStream, AdditionalDataBuilderFactory additionalDataBuilderFactory, Instantiator instantiator, PayloadSerializer payloadSerializer) {
        this(additionalDataBuilderFactory, instantiator, payloadSerializer);
        this.problemStream = problemStream;
    }

    public DefaultProblemBuilder(InternalProblem problem, AdditionalDataBuilderFactory additionalDataBuilderFactory, Instantiator instantiator, PayloadSerializer payloadSerializer) {
        this(additionalDataBuilderFactory, instantiator, payloadSerializer);
        this.id = problem.getDefinition().getId();
        this.contextualLabel = problem.getContextualLabel();
        this.solutions = new ArrayList<String>(problem.getSolutions());
        this.severity = problem.getDefinition().getSeverity();
        this.locations.addAll(problem.getOriginLocations());
        this.contextLocations.addAll(problem.getContextualLocations());
        this.details = problem.getDetails();
        this.docLink = problem.getDefinition().getDocumentationLink();
        this.exception = problem.getException();
        this.additionalData = problem.getAdditionalData();
        this.problemStream = null;
    }

    @Override
    public InternalProblem build() {
        // id is mandatory
        if (getId() == null) {
            return invalidProblem("missing-id", "Problem id must be specified", null);
        } else if (getId().getGroup() == null) {
            return invalidProblem("missing-parent", "Problem id must have a parent", null);
        }

        if (additionalData instanceof UnsupportedAdditionalDataSpec) {
            return invalidProblem("unsupported-additional-data", "Unsupported additional data type",
                "Unsupported additional data type: " + ((UnsupportedAdditionalDataSpec) additionalData).getType().getName() +
                    ". Supported types are: " + getAdditionalDataBuilderFactory().getSupportedTypes());
        }

        Throwable exceptionForProblemInstantiation = getExceptionForProblemInstantiation();
        if (problemStream != null) {
            addLocationsFromProblemStream(this.locations, exceptionForProblemInstantiation);
        }

        ProblemDefinition problemDefinition = new DefaultProblemDefinition(getId(), getSeverity(), docLink);
        return new DefaultProblem(
            problemDefinition,
            contextualLabel,
            solutions,
            locations,
            contextLocations,
            details,
            exceptionForProblemInstantiation,
            additionalData
        );
    }

    private void addLocationsFromProblemStream(List<ProblemLocation> locations, Throwable exceptionForProblemInstantiation) {
        assert problemStream != null;
        ProblemDiagnostics problemDiagnostics = problemStream.forCurrentCaller(exceptionForProblemInstantiation);
        Location loc = problemDiagnostics.getLocation();
        if (loc != null) {
            addFileLocationTo(locations, getFileLocation(loc));
        }
        if (problemDiagnostics.getSource() != null && problemDiagnostics.getSource().getPluginId() != null) {
            locations.add(getDefaultPluginIdLocation(problemDiagnostics));
        }
    }

    private static DefaultPluginIdLocation getDefaultPluginIdLocation(ProblemDiagnostics problemDiagnostics) {
        assert problemDiagnostics.getSource() != null;
        return new DefaultPluginIdLocation(problemDiagnostics.getSource().getPluginId());
    }

    private static FileLocation getFileLocation(Location loc) {
        String path = loc.getSourceLongDisplayName().getDisplayName();
        int line = loc.getLineNumber();
        return DefaultLineInFileLocation.from(path, line);
    }

    private InternalProblem invalidProblem(String id, String displayName, @Nullable String contextualLabel) {
        id(id, displayName, ProblemGroup.create(
            "problems-api",
            "Problems API")
        ).stackLocation();
        ProblemDefinition problemDefinition = new DefaultProblemDefinition(this.getId(), Severity.WARNING, null);
        Throwable exceptionForProblemInstantiation = getExceptionForProblemInstantiation();
        List<ProblemLocation> problemLocations = new ArrayList<ProblemLocation>();
        addLocationsFromProblemStream(problemLocations, exceptionForProblemInstantiation);
        return new DefaultProblem(problemDefinition,
            contextualLabel,
            ImmutableList.<String>of(),
            problemLocations,
            ImmutableList.<ProblemLocation>of(),
            null,
            exceptionForProblemInstantiation,
            null
        );
    }

    public Throwable getExceptionForProblemInstantiation() {
        return getException() == null && collectLocation ? new RuntimeException() : getException();
    }

    protected Severity getSeverity() {
        if (this.severity == null) {
            return Severity.WARNING;
        }
        return this.severity;
    }

    @Override
    public InternalProblemBuilder contextualLabel(String contextualLabel) {
        this.contextualLabel = contextualLabel;
        return this;
    }

    @Override
    public InternalProblemBuilder severity(Severity severity) {
        this.severity = severity;
        return this;
    }

    @Override
    public InternalProblemBuilder taskPathLocation(String buildTreePath) {
        this.contextLocations.add(new DefaultTaskPathLocation(buildTreePath));
        return this;
    }

    @Override
    public InternalProblemBuilder fileLocation(String path) {
        addFileLocation(DefaultFileLocation.from(path));
        return this;
    }

    @Override
    public InternalProblemBuilder lineInFileLocation(String path, int line) {
        return addFileLocation(DefaultLineInFileLocation.from(path, line));
    }

    @Nonnull
    private DefaultProblemBuilder addFileLocation(FileLocation from) {
        return addFileLocationTo(this.locations, from);
    }

    @Nonnull
    private DefaultProblemBuilder addFileLocationTo(List<ProblemLocation> problemLocations, FileLocation from) {
        if (problemLocations.contains(from)) {
            return this;
        }
        problemLocations.add(from);
        return this;
    }

    @Override
    public InternalProblemBuilder lineInFileLocation(String path, int line, int column) {
        addFileLocation(DefaultLineInFileLocation.from(path, line, column));
        return this;
    }

    @Override
    public InternalProblemBuilder offsetInFileLocation(String path, int offset, int length) {
        addFileLocation(DefaultOffsetInFileLocation.from(path, offset, length));
        return this;
    }

    @Override
    public InternalProblemBuilder lineInFileLocation(String path, int line, int column, int length) {
        addFileLocation(DefaultLineInFileLocation.from(path, line, column, length));
        return this;
    }

    @Override
    public InternalProblemBuilder stackLocation() {
        this.collectLocation = true;
        return this;
    }

    @Override
    public InternalProblemBuilder details(String details) {
        this.details = details;
        return this;
    }

    @Override
    public InternalProblemBuilder documentedAt(@Nullable DocLink doc) {
        this.docLink = doc;
        return this;
    }

    @Override
    public InternalProblemBuilder id(ProblemId problemId) {
        if (problemId instanceof DefaultProblemId) {
            this.id = problemId;
        } else {
            this.id = cloneId(problemId);
        }
        return this;
    }

    @Override
    public InternalProblemBuilder id(String name, String displayName, ProblemGroup parent) {
        this.id = ProblemId.create(name, displayName, cloneGroup(parent));
        return this;
    }

    private static ProblemId cloneId(ProblemId original) {
        return ProblemId.create(original.getName(), original.getDisplayName(), cloneGroup(original.getGroup()));
    }

    private static ProblemGroup cloneGroup(ProblemGroup original) {
        return ProblemGroup.create(original.getName(), original.getDisplayName(), original.getParent() == null ? null : cloneGroup(original.getParent()));
    }

    @Override
    public InternalProblemBuilder documentedAt(@Nullable String url) {
        this.docLink = url == null ? null : new DefaultDocLink(url);
        return this;
    }


    @Override
    public InternalProblemBuilder solution(@Nullable String solution) {
        if (this.solutions == null) {
            this.solutions = new ArrayList<String>();
        }
        this.solutions.add(solution);
        return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <U extends AdditionalDataSpec> InternalProblemBuilder additionalDataInternal(Class<? extends U> specType, Action<? super U> config) {
        if (getAdditionalDataBuilderFactory().hasProviderForSpec(specType)) {
            AdditionalDataBuilder<? extends AdditionalData> additionalDataBuilder = getAdditionalDataBuilderFactory().createAdditionalDataBuilder(specType, additionalData);
            config.execute((U) additionalDataBuilder);
            additionalData = additionalDataBuilder.build();
        } else {
            additionalData = new UnsupportedAdditionalDataSpec(specType);
        }
        return this;
    }

    @Override
    public <T extends AdditionalData> InternalProblemBuilder additionalData(Class<T> type, Action<? super T> config) {
        validateMethods(type);

        AdditionalData additionalDataInstance = createAdditionalData(type, config);
        Map<String, Object> methodValues = getAdditionalDataMap(type, additionalDataInstance);

        SerializedPayload payload = getPayloadSerializer().serialize(type);

        this.additionalData = new DefaultTypedAdditionalData(methodValues, payload);
        return this;
    }

    @Nonnull
    private static <T extends AdditionalData> Map<String, Object> getAdditionalDataMap(Class<T> type, AdditionalData additionalDataInstance) {
        Map<String, Object> methodValues = new HashMap<String, Object>();
        for (Method method : type.getMethods()) {
            Class<?> returnType = method.getReturnType();
            if (!void.class.equals(returnType) && method.getParameterCount() == 0) {
                try {
                    methodValues.put(method.getName(), additionalDataInstance.getClass().getMethod(method.getName(), method.getParameterTypes()).invoke(additionalDataInstance));
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                } catch (InvocationTargetException e) {
                    throw new RuntimeException(e);
                } catch (NoSuchMethodException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return methodValues;
    }

    @Nonnull
    private <T extends AdditionalData> AdditionalData createAdditionalData(Class<T> type, Action<? super T> config) {
        T additionalDataInstance = getInstantiator().newInstance(type);
        config.execute(additionalDataInstance);
        return additionalDataInstance;
    }

    @Override
    public <T extends AdditionalData> InternalProblemBuilder additionalDataInternal(T additionalDataInstance) {
        this.additionalData = additionalDataInstance;
        return this;
    }

    static <T extends AdditionalData> void validateMethods(Class<T> type) {
        Method[] methods = type.getMethods();
        for (Method method : methods) {
            String name = method.getName();
            if (!isValidGetter(method, name) && !isValidSetter(method, name)) {
                throw new IllegalArgumentException(getExceptionMessage(type));
            }
        }
    }

    private static String getExceptionMessage(Class<? extends AdditionalData> invalidType) {
        StringBuilder sb = new StringBuilder(invalidType.getSimpleName()).append(" must have only getters or setters using the following types: ");
        int size = TYPES.size();
        int index = 0;
        for (Class<?> type : TYPES) {
            sb.append(type.getSimpleName());
            index++;
            if (index < size - 1) {
                sb.append(", ");
            } else if (index == size - 1) {
                sb.append(", or ");
            }
        }

        sb.append(".");
        return sb.toString();
    }

    private static boolean isValidSetter(Method method, String name) {
        return name.startsWith("set") && method.getParameterCount() == 1 && TYPES.contains(method.getParameterTypes()[0]);
    }

    public final static Set<Class<?>> TYPES = ImmutableSet.<Class<?>>of(
        String.class,
        Boolean.class,
        Character.class,
        Byte.class,
        Short.class,
        Integer.class,
        Float.class,
        Long.class,
        Double.class,
        BigInteger.class,
        BigDecimal.class,
        File.class
    );

    private static boolean isValidGetter(Method method, String name) {
        return name.startsWith("get") && method.getParameterCount() == 0 && TYPES.contains(method.getReturnType());
    }


    @Override
    public InternalProblemBuilder withException(Throwable t) {
        this.exception = t;
        return this;
    }

    @Nullable
    Throwable getException() {
        return exception;
    }

    public ProblemId getId() {
        return id;
    }

    @Override
    public AdditionalDataBuilderFactory getAdditionalDataBuilderFactory() {
        return additionalDataBuilderFactory;
    }

    @Override
    public Instantiator getInstantiator() {
        return instantiator;
    }

    @Override
    public PayloadSerializer getPayloadSerializer() {
        return payloadSerializer;
    }

    private static class UnsupportedAdditionalDataSpec implements AdditionalData {

        private final Class<?> type;

        UnsupportedAdditionalDataSpec(Class<?> type) {
            this.type = type;
        }

        public Class<?> getType() {
            return type;
        }
    }
}
