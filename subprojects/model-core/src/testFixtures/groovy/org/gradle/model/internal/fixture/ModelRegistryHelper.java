package org.gradle.model.internal.fixture;

import org.gradle.api.internal.rules.RuleAwarePolymorphicNamedEntityInstantiator;
import org.gradle.model.internal.inspect.MethodModelRuleExtractors;
import org.gradle.model.internal.inspect.ModelRuleExtractor;
import org.gradle.model.internal.manage.schema.extract.DefaultModelSchemaStore;
import org.gradle.model.internal.registry.DefaultModelRegistry;
import org.gradle.model.internal.type.ModelType;

public class ModelRegistryHelper extends DefaultModelRegistry {
    public ModelRegistryHelper() {
        super(new ModelRuleExtractor(MethodModelRuleExtractors.coreExtractors(DefaultModelSchemaStore.getInstance())));
    }

    public ModelRegistryHelper(ModelRuleExtractor ruleExtractor) {
        super(ruleExtractor);
    }

    public static <T> ModelType<RuleAwarePolymorphicNamedEntityInstantiator<T>> instantiatorType(Class<T> typeClass) {
        return new ModelType.Builder<RuleAwarePolymorphicNamedEntityInstantiator<T>>() {
        }.where(new ModelType.Parameter<T>() {
        }, ModelType.of(typeClass)).build();
    }
}
