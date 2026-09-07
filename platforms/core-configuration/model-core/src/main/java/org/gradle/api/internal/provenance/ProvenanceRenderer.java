/*
 * Copyright 2026 Gradle and contributors.
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

package org.gradle.api.internal.provenance;

import org.jspecify.annotations.Nullable;

import java.util.Iterator;

/** Bounded, stacktrace-like projection of descriptor facts. Never inspects values or provider graphs. */
public final class ProvenanceRenderer {
    private static final int UPDATE_LIMIT = 64;
    private static final int DETAIL_LIMIT = 16;
    private static final int LABEL_LIMIT = 512;

    private ProvenanceRenderer() {
    }

    public static String configuration(EffectiveProvenanceView view) {
        return render("Configuration trace to source", view, null);
    }

    public static String failure(EffectiveProvenanceView view, @Nullable FailedOperation failedOperation) {
        return render("Failure trace to source", view, failedOperation);
    }

    private static String render(String heading, EffectiveProvenanceView view, @Nullable FailedOperation failedOperation) {
        StringBuilder result = new StringBuilder(512);
        result.append(heading).append(" for ").append(label(view.getTarget().getModelPath()));
        result.append(" (build '").append(label(view.getTarget().getOwner().getBuildIdentity()));
        result.append("', project '").append(label(view.getTarget().getOwner().getScopePath())).append("'):\n");
        if (failedOperation != null) {
            frame(result, "failed " + label(failedOperation.getOperation()), failedOperation.getAttribution());
        }
        Iterator<MutationOccurrence> updates = view.getUpdates().reverseIterator();
        int shown = 0;
        while (shown < UPDATE_LIMIT && updates.hasNext()) {
            MutationOccurrence occurrence = updates.next();
            StringBuilder operation = new StringBuilder("update ");
            int shapes = 0;
            for (SemanticOperation.Shape shape : occurrence.getOperation().getShapes()) {
                if (shapes == DETAIL_LIMIT) {
                    operation.append(" -> ...");
                    break;
                }
                if (shapes++ != 0) {
                    operation.append(" -> ");
                }
                operation.append(shape.name().toLowerCase(java.util.Locale.ROOT));
            }
            frame(result, operation.toString(), occurrence.getAttribution());
            shown++;
        }
        if (shown < view.getUpdates().size()) {
            result.append("    ... ").append(view.getUpdates().size() - shown).append(" earlier updates omitted\n");
        }
        source(result, view.getSource(), view.getRootKind());
        if (!view.getShadowedConfiguration().isEmpty()) {
            result.append("Shadowed configuration (not selected):\n");
            int count = 0;
            for (MutationOccurrence binding : view.getShadowedConfiguration()) {
                if (count++ == DETAIL_LIMIT) {
                    result.append("    ... additional shadowed bindings omitted\n");
                    break;
                }
                frame(result, binding.getOperation().getKind() == SemanticOperation.Kind.CONVENTION_BINDING ? "convention" : "binding", binding.getAttribution());
            }
        }
        if (!view.isCompleteLocal()) {
            result.append("Partial local provenance:\n");
            int count = 0;
            for (String reason : view.getPartialReasons()) {
                if (count++ == DETAIL_LIMIT) {
                    result.append("    ... additional coverage notes omitted\n");
                    break;
                }
                result.append("    ").append(label(reason)).append('\n');
            }
        }
        result.append("Local configuration only; provider dependencies and failure causality are not inferred.");
        return result.toString();
    }

    private static void source(StringBuilder result, EffectiveProvenanceView.Source source, EffectiveProvenanceView.RootKind rootKind) {
        String selection = source.getSelection().name().toLowerCase(java.util.Locale.ROOT) + " source";
        if (source.getSelection() == EffectiveProvenanceView.SourceSelection.CONVENTION) {
            selection += rootKind == EffectiveProvenanceView.RootKind.CAPTURED ? " (captured)" : " (live convention)";
        }
        switch (source.getKnowledge()) {
            case KNOWN:
                MutationOccurrence occurrence = java.util.Objects.requireNonNull(source.getOccurrence());
                if (occurrence.getOperation().getKind() == SemanticOperation.Kind.UNCLASSIFIED_BINDING) {
                    selection += " (unclassified binding)";
                }
                frame(result, selection, occurrence.getAttribution());
                break;
            case UNCONFIGURED:
                result.append("    at source (unconfigured)\n");
                break;
            case UNATTRIBUTED:
                result.append("    at ").append(selection).append(" (unattributed)\n");
                break;
            case UNAVAILABLE:
                result.append("    at ").append(selection).append(" (provenance unavailable: ").append(label(source.getReason())).append(")\n");
                break;
            default:
                throw new IllegalArgumentException("Unknown source knowledge.");
        }
    }

    private static void frame(StringBuilder result, String operation, @Nullable Attribution attribution) {
        result.append("    at ").append(operation).append(" [");
        if (attribution == null) {
            result.append("unknown caller origin");
        } else {
            DiagnosticOrigin origin = attribution.getOrigin();
            switch (origin.getKind()) {
                case PLUGIN_ID:
                    result.append("plugin '").append(label(origin.getIdentifier())).append("'");
                    break;
                case PLUGIN_CLASS:
                    result.append("plugin class '").append(label(origin.getIdentifier())).append("'");
                    break;
                case UNKNOWN:
                    result.append("unknown origin");
                    break;
                default:
                    result.append(label(origin.getDisplayName()));
            }
            result.append("; source build '").append(label(attribution.getSourceScope().getBuildIdentity()));
            result.append("', scope '").append(label(attribution.getSourceScope().getScopePath())).append("'");
        }
        result.append("]\n");
    }

    /** Keep user-controlled descriptor labels on one bounded line. */
    private static String label(String value) {
        StringBuilder result = new StringBuilder(Math.min(value.length(), LABEL_LIMIT));
        int length = Math.min(value.length(), LABEL_LIMIT);
        for (int i = 0; i < length; i++) {
            char character = value.charAt(i);
            switch (character) {
                case '\n': result.append("\\n"); break;
                case '\r': result.append("\\r"); break;
                case '\t': result.append("\\t"); break;
                default: result.append(Character.isISOControl(character) ? '?' : character);
            }
        }
        if (value.length() > length) {
            result.append("...");
        }
        return result.toString();
    }
}
