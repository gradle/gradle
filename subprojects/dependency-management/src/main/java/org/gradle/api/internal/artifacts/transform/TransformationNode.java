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

package org.gradle.api.internal.artifacts.transform;

import org.gradle.api.Describable;
import org.gradle.api.artifacts.ResolveException;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.component.OpaqueComponentIdentifier;
import org.gradle.api.internal.artifacts.configurations.ConfigurationIdentity;
import org.gradle.api.internal.artifacts.ivyservice.DefaultLenientConfiguration;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact;
import org.gradle.api.internal.capabilities.Capability;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.NodeExecutionContext;
import org.gradle.api.internal.tasks.TaskDependencyContainer;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.execution.plan.CreationOrderedNode;
import org.gradle.execution.plan.Node;
import org.gradle.execution.plan.SelfExecutingNode;
import org.gradle.execution.plan.TaskDependencyResolver;
import org.gradle.internal.Describables;
import org.gradle.internal.Try;
import org.gradle.internal.model.CalculatedValueContainer;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.gradle.internal.model.ValueCalculator;
import org.gradle.internal.operations.BuildOperationCategory;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.CallableBuildOperation;
import org.gradle.internal.scan.UsedByScanPlugin;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public abstract class TransformationNode extends CreationOrderedNode implements SelfExecutingNode {

    private static final AtomicLong SEQUENCE = new AtomicLong();

    protected final TransformationStep transformationStep;
    protected final ResolvableArtifact artifact;
    private final ComponentVariantIdentifier targetComponentVariant;
    private final AttributeContainer sourceAttributes;
    protected final TransformUpstreamDependencies upstreamDependencies;
    private final long transformationNodeId;

    private TransformationIdentity cachedIdentity;

    public static ChainedTransformationNode chained(
        ComponentVariantIdentifier targetComponentVariant,
        AttributeContainer sourceAttributes,
        TransformationStep current,
        TransformationNode previous,
        TransformUpstreamDependencies upstreamDependencies,
        BuildOperationExecutor buildOperationExecutor,
        CalculatedValueContainerFactory calculatedValueContainerFactory
    ) {
        return new ChainedTransformationNode(targetComponentVariant, sourceAttributes, current, previous, upstreamDependencies, buildOperationExecutor, calculatedValueContainerFactory);
    }

    public static InitialTransformationNode initial(
        ComponentVariantIdentifier targetComponentVariant,
        AttributeContainer sourceAttributes,
        TransformationStep initial,
        ResolvableArtifact artifact,
        TransformUpstreamDependencies upstreamDependencies,
        BuildOperationExecutor buildOperationExecutor,
        CalculatedValueContainerFactory calculatedValueContainerFactory
    ) {
        return new InitialTransformationNode(targetComponentVariant, sourceAttributes, initial, artifact, upstreamDependencies, buildOperationExecutor, calculatedValueContainerFactory);
    }

    private static long createId() {
        return SEQUENCE.incrementAndGet();
    }

    protected TransformationNode(
        ComponentVariantIdentifier targetComponentVariant,
        AttributeContainer sourceAttributes,
        TransformationStep transformationStep,
        ResolvableArtifact artifact,
        TransformUpstreamDependencies upstreamDependencies
    ) {
        this.targetComponentVariant = targetComponentVariant;
        this.sourceAttributes = sourceAttributes;
        this.transformationStep = transformationStep;
        this.artifact = artifact;
        this.upstreamDependencies = upstreamDependencies;
        this.transformationNodeId = createId();
    }

    public ComponentVariantIdentifier getTargetComponentVariant() {
        return targetComponentVariant;
    }

    public AttributeContainer getSourceAttributes() {
        return sourceAttributes;
    }

    public TransformationIdentity getNodeIdentity() {
        if (cachedIdentity == null) {
            cachedIdentity = createIdentity();
        }
        return cachedIdentity;
    }

    private TransformationIdentity createIdentity() {
        String consumerBuildPath = transformationStep.getOwningProject().getBuildPath().toString();
        String consumerProjectPath = transformationStep.getOwningProject().getIdentityPath().toString();
        ComponentIdentifier componentId = getComponentIdentifier(targetComponentVariant.getComponentId());
        Map<String, String> targetAttributes = AttributesToMapConverter.convertToMap(targetComponentVariant.getAttributes());
        List<Capability> capabilities = targetComponentVariant.getCapabilities().stream()
            .map(TransformationNode::convertCapability)
            .collect(Collectors.toList());

        return new TransformationIdentity() {
            @Override
            public String getBuildPath() {
                return consumerBuildPath;
            }

            @Override
            public String getProjectPath() {
                return consumerProjectPath;
            }

            @Override
            public ComponentIdentifier getComponentId() {
                return componentId;
            }

            @Override
            public Map<String, String> getTargetAttributes() {
                return targetAttributes;
            }

            @Override
            public List<? extends Capability> getCapabilities() {
                return capabilities;
            }

            @Override
            public String getArtifactName() {
                return artifact.getArtifactName().toString();
            }

            @Override
            public ConfigurationIdentity getDependenciesConfigurationIdentity() {
                return upstreamDependencies.getConfigurationIdentity();
            }

            @Override
            public long getTransformationNodeId() {
                return transformationNodeId;
            }

            @Override
            public String toString() {
                return "Transform '" + targetComponentVariant.getComponentId() + "' with " + transformationStep.getTransformer().getImplementationClass().getName();
            }
        };
    }

    private static Capability convertCapability(org.gradle.api.capabilities.Capability capability) {
        return new Capability() {
            @Override
            public String getGroup() {
                return capability.getGroup();
            }

            @Override
            public String getName() {
                return capability.getName();
            }

            @Override
            public String getVersion() {
                return capability.getVersion();
            }

            @Override
            public String toString() {
                return getGroup() + ":" + getName() + (getVersion() == null ? "" : (":" + getVersion()));
            }
        };
    }

    private static ComponentIdentifier getComponentIdentifier(org.gradle.api.artifacts.component.ComponentIdentifier componentId) {
        if (componentId instanceof ProjectComponentIdentifier) {
            ProjectComponentIdentifier projectComponentIdentifier = (ProjectComponentIdentifier) componentId;
            return new org.gradle.api.internal.artifacts.component.ProjectComponentIdentifier() {
                @Override
                public String getBuildPath() {
                    return projectComponentIdentifier.getBuild().getName();
                }

                @Override
                public String getProjectPath() {
                    return projectComponentIdentifier.getProjectPath();
                }

                @Override
                public String toString() {
                    return projectComponentIdentifier.getDisplayName();
                }
            };
        } else if (componentId instanceof ModuleComponentIdentifier) {
            ModuleComponentIdentifier moduleComponentIdentifier = (ModuleComponentIdentifier) componentId;
            return new org.gradle.api.internal.artifacts.component.ModuleComponentIdentifier() {
                @Override
                public String getGroup() {
                    return moduleComponentIdentifier.getGroup();
                }

                @Override
                public String getModule() {
                    return moduleComponentIdentifier.getModule();
                }

                @Override
                public String getVersion() {
                    return moduleComponentIdentifier.getVersion();
                }

                @Override
                public String toString() {
                    return moduleComponentIdentifier.getDisplayName();
                }
            };
        } else {
            return new OpaqueComponentIdentifier() {
                @Override
                public String getDisplayName() {
                    return componentId.getDisplayName();
                }

                @Override
                public String getClassName() {
                    return componentId.getClass().getName();
                }

                @Override
                public String toString() {
                    return componentId.getDisplayName();
                }
            };
        }
    }

    public ResolvableArtifact getInputArtifact() {
        return artifact;
    }

    public TransformUpstreamDependencies getUpstreamDependencies() {
        return upstreamDependencies;
    }

    @Nullable
    @Override
    public ProjectInternal getOwningProject() {
        return transformationStep.getOwningProject();
    }

    @Override
    public boolean isPublicNode() {
        return true;
    }

    @Override
    public String toString() {
        return transformationStep.getDisplayName();
    }

    public TransformationStep getTransformationStep() {
        return transformationStep;
    }

    public Try<TransformationSubject> getTransformedSubject() {
        return getTransformedArtifacts().getValue();
    }

    @Override
    public void execute(NodeExecutionContext context) {
        getTransformedArtifacts().run(context);
    }

    public void executeIfNotAlready() {
        transformationStep.isolateParametersIfNotAlready();
        upstreamDependencies.finalizeIfNotAlready();
        getTransformedArtifacts().finalizeIfNotAlready();
    }

    protected abstract CalculatedValueContainer<TransformationSubject, ?> getTransformedArtifacts();

    @Override
    public Throwable getNodeFailure() {
        return null;
    }

    @Override
    public void resolveDependencies(TaskDependencyResolver dependencyResolver) {
        processDependencies(dependencyResolver.resolveDependenciesFor(null, (TaskDependencyContainer) context -> getTransformedArtifacts().visitDependencies(context)));
    }

    protected void processDependencies(Set<Node> dependencies) {
        for (Node dependency : dependencies) {
            addDependencySuccessor(dependency);
        }
    }

    public static class InitialTransformationNode extends TransformationNode {
        private final CalculatedValueContainer<TransformationSubject, TransformInitialArtifact> result;

        public InitialTransformationNode(
            ComponentVariantIdentifier targetComponentVariant,
            AttributeContainer sourceAttributes,
            TransformationStep transformationStep,
            ResolvableArtifact artifact,
            TransformUpstreamDependencies upstreamDependencies,
            BuildOperationExecutor buildOperationExecutor,
            CalculatedValueContainerFactory calculatedValueContainerFactory
        ) {
            super(targetComponentVariant, sourceAttributes, transformationStep, artifact, upstreamDependencies);
            result = calculatedValueContainerFactory.create(Describables.of(this), new TransformInitialArtifact(buildOperationExecutor));
        }

        @Override
        protected CalculatedValueContainer<TransformationSubject, TransformInitialArtifact> getTransformedArtifacts() {
            return result;
        }

        private class TransformInitialArtifact implements ValueCalculator<TransformationSubject> {
            private final BuildOperationExecutor buildOperationExecutor;

            public TransformInitialArtifact(BuildOperationExecutor buildOperationExecutor) {
                this.buildOperationExecutor = buildOperationExecutor;
            }

            @Override
            public void visitDependencies(TaskDependencyResolveContext context) {
                context.add(transformationStep);
                context.add(upstreamDependencies);
                context.add(artifact);
            }

            @Override
            public TransformationSubject calculateValue(NodeExecutionContext context) {
                return buildOperationExecutor.call(new ArtifactTransformationStepBuildOperation() {
                    @Override
                    protected TransformationSubject transform() {
                        TransformationSubject initialArtifactTransformationSubject;
                        try {
                            initialArtifactTransformationSubject = TransformationSubject.initial(artifact);
                        } catch (ResolveException e) {
                            throw e;
                        } catch (RuntimeException e) {
                            throw new DefaultLenientConfiguration.ArtifactResolveException("artifacts", transformationStep.getDisplayName(), "artifact transform", Collections.singleton(e));
                        }

                        return transformationStep
                            .createInvocation(initialArtifactTransformationSubject, upstreamDependencies, context)
                            .completeAndGet()
                            .get();
                    }

                    @Override
                    protected String describeSubject() {
                        return artifact.getId().getDisplayName();
                    }
                });
            }
        }
    }

    public static class ChainedTransformationNode extends TransformationNode {
        private final TransformationNode previousTransformationNode;
        private final CalculatedValueContainer<TransformationSubject, TransformPreviousArtifacts> result;

        public ChainedTransformationNode(
            ComponentVariantIdentifier targetComponentVariant,
            AttributeContainer sourceAttributes,
            TransformationStep transformationStep,
            TransformationNode previousTransformationNode,
            TransformUpstreamDependencies upstreamDependencies,
            BuildOperationExecutor buildOperationExecutor,
            CalculatedValueContainerFactory calculatedValueContainerFactory
        ) {
            super(targetComponentVariant, sourceAttributes, transformationStep, previousTransformationNode.artifact, upstreamDependencies);
            this.previousTransformationNode = previousTransformationNode;
            result = calculatedValueContainerFactory.create(Describables.of(this), new TransformPreviousArtifacts(buildOperationExecutor));
        }

        public TransformationNode getPreviousTransformationNode() {
            return previousTransformationNode;
        }

        @Override
        protected CalculatedValueContainer<TransformationSubject, TransformPreviousArtifacts> getTransformedArtifacts() {
            return result;
        }

        @Override
        public void executeIfNotAlready() {
            // Only finalize the previous node when executing this node on demand
            previousTransformationNode.executeIfNotAlready();
            super.executeIfNotAlready();
        }

        private class TransformPreviousArtifacts implements ValueCalculator<TransformationSubject> {
            private final BuildOperationExecutor buildOperationExecutor;

            public TransformPreviousArtifacts(BuildOperationExecutor buildOperationExecutor) {
                this.buildOperationExecutor = buildOperationExecutor;
            }

            @Override
            public void visitDependencies(TaskDependencyResolveContext context) {
                context.add(transformationStep);
                context.add(upstreamDependencies);
                context.add(new DefaultTransformationDependency(Collections.singletonList(previousTransformationNode)));
            }

            @Override
            public TransformationSubject calculateValue(NodeExecutionContext context) {
                return buildOperationExecutor.call(new ArtifactTransformationStepBuildOperation() {
                    @Override
                    protected TransformationSubject transform() {
                        return previousTransformationNode.getTransformedSubject()
                            .flatMap(transformedSubject -> transformationStep
                                .createInvocation(transformedSubject, upstreamDependencies, context)
                                .completeAndGet())
                            .get();
                    }

                    @Override
                    protected String describeSubject() {
                        return previousTransformationNode.getTransformedSubject()
                            .map(Describable::getDisplayName)
                            .getOrMapFailure(Throwable::getMessage);
                    }
                });
            }
        }
    }

    private abstract class ArtifactTransformationStepBuildOperation implements CallableBuildOperation<TransformationSubject> {

        @UsedByScanPlugin("The string is used for filtering out artifact transform logs in Gradle Enterprise")
        private static final String TRANSFORMING_PROGRESS_PREFIX = "Transforming ";

        @Override
        public final BuildOperationDescriptor.Builder description() {
            String transformerName = transformationStep.getDisplayName();
            String subjectName = describeSubject();
            String basicName = subjectName + " with " + transformerName;
            return BuildOperationDescriptor.displayName("Transform " + basicName)
                .progressDisplayName(TRANSFORMING_PROGRESS_PREFIX + basicName)
                .metadata(BuildOperationCategory.TRANSFORM)
                .details(new ExecuteScheduledTransformationStepBuildOperationDetails(TransformationNode.this, transformerName, subjectName));
        }

        protected abstract String describeSubject();

        @Override
        public TransformationSubject call(BuildOperationContext context) {
            context.setResult(RESULT);
            return transform();
        }

        protected abstract TransformationSubject transform();
    }

    private static final ExecuteScheduledTransformationStepBuildOperationType.Result RESULT = new ExecuteScheduledTransformationStepBuildOperationType.Result() {
    };

}
