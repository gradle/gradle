/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.reporting.components.internal;

import com.google.common.collect.Maps;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.tasks.diagnostics.internal.text.TextReportBuilder;
import org.gradle.internal.text.TreeFormatter;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.model.ModelMap;
import org.gradle.model.internal.manage.schema.ModelProperty;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.model.internal.manage.schema.StructSchema;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.internal.BinaryBuildAbility;
import org.gradle.platform.base.internal.BinarySpecInternal;
import org.gradle.platform.base.internal.VariantAspect;
import org.gradle.reporting.ReportRenderer;
import org.gradle.util.GUtil;

import java.util.Map;

// TODO - bust up this hierarchy and compose using interfaces instead
public abstract class AbstractBinaryRenderer<T extends BinarySpec> extends ReportRenderer<BinarySpec, TextReportBuilder> {
    private ModelSchemaStore schemaStore;

    protected AbstractBinaryRenderer(ModelSchemaStore schemaStore) {
        this.schemaStore = schemaStore;
    }

    @Override
    public void render(BinarySpec binary, TextReportBuilder builder) {
        String heading = StringUtils.capitalize(binary.getDisplayName());
        if (!binary.isBuildable()) {
            heading += " (not buildable)";
        }
        builder.heading(heading);

        builder.item("build using task", binary.getBuildTask().getPath());

        T specialized = getTargetType().cast(binary);

        renderTasks(specialized, builder);

        renderVariants(specialized, builder);

        renderDetails(specialized, builder);

        renderOutputs(specialized, builder);

        renderBuildAbility(specialized, builder);

        renderOwnedSourceSets(specialized, builder);
    }

    public abstract Class<T> getTargetType();

    protected void renderOutputs(T binary, TextReportBuilder builder) {
    }

    protected void renderVariants(T binary, TextReportBuilder builder) {
        ModelSchema<?> schema = schemaStore.getSchema(((BinarySpecInternal)binary).getPublicType());
        if (!(schema instanceof StructSchema)) {
            return;
        }
        Map<String, Object> variants = Maps.newTreeMap();
        VariantAspect variantAspect = ((StructSchema<?>) schema).getAspect(VariantAspect.class);
        if (variantAspect != null) {
            for (ModelProperty<?> property : variantAspect.getDimensions()) {
                variants.put(property.getName(), property.getPropertyValue(binary));
            }
        }

        for (Map.Entry<String, Object> variant : variants.entrySet()) {
            String variantName = GUtil.toWords(variant.getKey());
            builder.item(variantName, RendererUtils.displayValueOf(variant.getValue()));
        }
    }

    protected void renderDetails(T binary, TextReportBuilder builder) {
    }

    protected void renderTasks(T binary, TextReportBuilder builder) {
    }

    private void renderBuildAbility(BinarySpec binary, TextReportBuilder builder) {
        BinaryBuildAbility buildAbility = ((BinarySpecInternal) binary).getBuildAbility();
        if (!buildAbility.isBuildable()) {
            TreeFormatter formatter = new TreeFormatter();
            buildAbility.explain(formatter);
            builder.item(formatter.toString());
        }
    }

    protected void renderOwnedSourceSets(T binary, TextReportBuilder builder) {
        if (((BinarySpecInternal) binary).isLegacyBinary()) {
            return;
        }
        ModelMap<LanguageSourceSet> sources = binary.getSources();
        if (!sources.isEmpty()) {
            SourceSetRenderer sourceSetRenderer = new SourceSetRenderer();
            builder.collection("source sets", sources.values(), sourceSetRenderer, "source sets");
        }
    }
}
