package com.obe.mapperstudio.task.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.salilvnair.convengine.llm.core.LlmClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MappingSuggestionService {

    private final ObjectMapper mapper;
    private final LlmClient llmClient;

    public List<Map<String, Object>> generateSuggestions(
            List<Map<String, Object>> sourceFields,
            List<Map<String, Object>> targetFields
    ) {
        List<Map<String, Object>> suggestions = buildSuggestions(sourceFields, targetFields);
        if (suggestions.isEmpty()) {
            suggestions = buildSuggestionsWithAi(sourceFields, targetFields);
        }
        suggestions = fillMissingTargetsWithEmbeddings(suggestions, sourceFields, targetFields);
        if (suggestions.isEmpty()) {
            suggestions = buildSuggestions(sourceFields, targetFields);
        }
        return suggestions;
    }

    private List<Map<String, Object>> buildSuggestions(List<Map<String, Object>> sourceFields, List<Map<String, Object>> targetFields) {
        if (sourceFields.isEmpty() || targetFields.isEmpty()) {
            return List.of();
        }

        List<Map<String, Object>> suggestions = new ArrayList<>();
        Set<String> usedSourcePaths = new LinkedHashSet<>();

        for (Map<String, Object> target : targetFields) {
            String targetPath = String.valueOf(target.getOrDefault("path", ""));
            if (targetPath.isBlank()) {
                continue;
            }

            Match best = null;
            for (Map<String, Object> source : sourceFields) {
                String sourcePath = String.valueOf(source.getOrDefault("path", ""));
                if (sourcePath.isBlank() || usedSourcePaths.contains(sourcePath)) {
                    continue;
                }
                Match candidate = score(sourcePath, targetPath);
                if (best == null || candidate.score > best.score) {
                    best = candidate;
                }
            }

            if (best == null || best.score < 0.35d) {
                continue;
            }

            usedSourcePaths.add(best.sourcePath);
            double confidence = Math.min(0.95d, Math.max(0.60d, 0.50d + best.score * 0.45d));

            suggestions.add(Map.of(
                    "sourcePath", best.sourcePath,
                    "targetPath", targetPath,
                    "confidence", round2(confidence),
                    "transformType", "DIRECT",
                    "reason", best.reason,
                    "targetArtifactName", asString(target.get("targetArtifactName")),
                    "targetArtifactType", asString(target.get("targetArtifactType"))
            ));
        }

        return suggestions;
    }

    private Match score(String sourcePath, String targetPath) {
        Set<String> sourceTokens = normalizeTokens(sourcePath);
        Set<String> targetTokens = normalizeTokens(targetPath);
        if (sourceTokens.isEmpty() || targetTokens.isEmpty()) {
            return new Match(sourcePath, 0d, "Low semantic similarity");
        }

        int overlap = 0;
        for (String token : sourceTokens) {
            if (targetTokens.contains(token)) {
                overlap++;
            }
        }

        double overlapScore = (double) overlap / (double) Math.max(1, targetTokens.size());
        String sourceLeaf = leaf(sourcePath);
        String targetLeaf = leaf(targetPath);

        double leafBonus = sourceLeaf.equalsIgnoreCase(targetLeaf) ? 0.35d : 0d;
        double score = overlapScore + leafBonus;
        String reason = sourceLeaf.equalsIgnoreCase(targetLeaf)
                ? "Field name and type exact match"
                : "Semantic and description similarity";
        return new Match(sourcePath, score, reason);
    }

    private Set<String> normalizeTokens(String path) {
        if (path == null || path.isBlank()) {
            return Set.of();
        }
        String[] segments = path.split("[.\\[\\]_\\- ]+");
        Set<String> out = new LinkedHashSet<>();
        for (String raw : segments) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String spaced = raw.replaceAll("([a-z])([A-Z])", "$1 $2");
            String[] parts = spaced.toLowerCase(Locale.ROOT).split("\\s+");
            for (String p : parts) {
                String token = p.trim();
                if (!token.isBlank()) {
                    out.add(token);
                }
            }
        }
        return out;
    }

    private String leaf(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        String normalized = path.replaceAll("\\[[0-9]+\\]", "");
        int i = normalized.lastIndexOf('.');
        return i >= 0 ? normalized.substring(i + 1).toLowerCase(Locale.ROOT) : normalized.toLowerCase(Locale.ROOT);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> buildSuggestionsWithAi(
            List<Map<String, Object>> sourceFields,
            List<Map<String, Object>> targetFields
    ) {
        if (sourceFields.isEmpty() || targetFields.isEmpty()) {
            return List.of();
        }

        try {
            String hint = """
                    Generate source-to-target mapping suggestions.
                    Return top matches for target fields based on semantics and field intent.
                    Use DIRECT transform when no transformation is needed.
                    Prefer covering all required target fields first.
                    """;

            String responseSchema = """
                    {
                      "type": "object",
                      "properties": {
                        "suggestions": {
                          "type": "array",
                          "items": {
                            "type": "object",
                            "properties": {
                              "sourcePath": {"type":"string"},
                              "targetPath": {"type":"string"},
                              "confidence": {"type":"number"},
                              "transformType": {"type":"string"},
                              "reason": {"type":"string"}
                            },
                            "required": ["sourcePath", "targetPath", "confidence", "transformType", "reason"],
                            "additionalProperties": false
                          }
                        }
                      },
                      "required": ["suggestions"],
                      "additionalProperties": false
                    }
                    """;

            String context = mapper.writeValueAsString(Map.of(
                    "sourceFields", sourceFields,
                    "targetFields", targetFields,
                    "instructions", "Map each target path to the most appropriate source path. Confidence should be between 0 and 1. Ensure required target fields are not skipped."
            ));

            String raw = llmClient.generateJsonStrict(hint, responseSchema, context);
            Map<String, Object> parsed = mapper.readValue(raw, Map.class);
            Object suggestionsRaw = parsed.get("suggestions");
            if (!(suggestionsRaw instanceof List<?> list)) {
                return List.of();
            }

            List<Map<String, Object>> suggestions = new ArrayList<>();
            Set<String> allowedTargets = targetFields.stream()
                    .map(v -> String.valueOf(v.getOrDefault("path", "")))
                    .filter(v -> !v.isBlank())
                    .collect(Collectors.toSet());
            Map<String, Map<String, Object>> targetByPath = targetFields.stream()
                    .filter(v -> !asString(v.get("path")).isBlank())
                    .collect(Collectors.toMap(v -> asString(v.get("path")), v -> v, (a, b) -> a, LinkedHashMap::new));
            Set<String> allowedSources = sourceFields.stream()
                    .map(v -> String.valueOf(v.getOrDefault("path", "")))
                    .filter(v -> !v.isBlank())
                    .collect(Collectors.toSet());

            for (Object item : list) {
                if (!(item instanceof Map<?, ?> row)) {
                    continue;
                }
                String sourcePath = asString(row.get("sourcePath")).trim();
                String targetPath = asString(row.get("targetPath")).trim();
                if (!allowedSources.contains(sourcePath) || !allowedTargets.contains(targetPath)) {
                    continue;
                }
                double confidence;
                Object confidenceRaw = row.get("confidence");
                if (confidenceRaw instanceof Number num) {
                    confidence = num.doubleValue();
                } else {
                    try {
                        confidence = Double.parseDouble(String.valueOf(confidenceRaw));
                    } catch (Exception ignored) {
                        confidence = 0.0d;
                    }
                }
                confidence = Math.max(0.0d, Math.min(1.0d, confidence));

                Map<String, Object> suggestion = new LinkedHashMap<>();
                suggestion.put("sourcePath", sourcePath);
                suggestion.put("targetPath", targetPath);
                suggestion.put("confidence", round2(confidence));
                suggestion.put("transformType", asString(row.get("transformType"), "DIRECT"));
                suggestion.put("reason", asString(row.get("reason"), "AI semantic mapping"));
                Map<String, Object> targetMeta = targetByPath.get(targetPath);
                if (targetMeta != null) {
                    suggestion.put("targetArtifactName", asString(targetMeta.get("targetArtifactName")));
                    suggestion.put("targetArtifactType", asString(targetMeta.get("targetArtifactType")));
                }
                suggestions.add(suggestion);
            }
            return suggestions;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<Map<String, Object>> fillMissingTargetsWithEmbeddings(
            List<Map<String, Object>> existing,
            List<Map<String, Object>> sourceFields,
            List<Map<String, Object>> targetFields
    ) {
        if (sourceFields.isEmpty() || targetFields.isEmpty()) {
            return existing;
        }

        List<Map<String, Object>> out = new ArrayList<>(existing);
        Set<String> coveredTargets = out.stream()
                .map(s -> asString(s.get("targetPath")))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> usedSources = out.stream()
                .map(s -> asString(s.get("sourcePath")))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<String, float[]> sourceEmbeddings = new LinkedHashMap<>();
        for (Map<String, Object> src : sourceFields) {
            String sourcePath = asString(src.get("path")).trim();
            if (sourcePath.isBlank()) {
                continue;
            }
            sourceEmbeddings.put(sourcePath, embeddingForField(sourcePath, src));
        }

        for (Map<String, Object> target : targetFields) {
            String targetPath = asString(target.get("path")).trim();
            if (targetPath.isBlank() || coveredTargets.contains(targetPath)) {
                continue;
            }
            boolean required = Boolean.parseBoolean(String.valueOf(target.getOrDefault("required", false)));

            float[] targetVec = embeddingForField(targetPath, target);
            String bestSource = null;
            double bestScore = -1d;

            for (Map<String, Object> source : sourceFields) {
                String sourcePath = asString(source.get("path")).trim();
                if (sourcePath.isBlank() || usedSources.contains(sourcePath)) {
                    continue;
                }
                float[] sourceVec = sourceEmbeddings.get(sourcePath);
                double score = cosine(sourceVec, targetVec);
                if (score > bestScore) {
                    bestScore = score;
                    bestSource = sourcePath;
                }
            }

            double minScore = required ? 0.25d : 0.40d;
            if (bestSource == null || bestScore < minScore) {
                continue;
            }

            Map<String, Object> suggestion = new LinkedHashMap<>();
            suggestion.put("sourcePath", bestSource);
            suggestion.put("targetPath", targetPath);
            suggestion.put("confidence", round2(Math.max(0.55d, Math.min(0.95d, bestScore))));
            suggestion.put("transformType", "DIRECT");
            suggestion.put("reason", "Semantic similarity (embedding)");
            suggestion.put("targetArtifactName", asString(target.get("targetArtifactName")));
            suggestion.put("targetArtifactType", asString(target.get("targetArtifactType")));
            out.add(suggestion);
            coveredTargets.add(targetPath);
            usedSources.add(bestSource);
        }
        return out;
    }

    private float[] embeddingForField(String path, Map<String, Object> field) {
        String type = asString(field.getOrDefault("type", "string"));
        String desc = asString(field.getOrDefault("description", ""));
        String text = "path: " + path + ", type: " + type + ", description: " + desc;
        try {
            return llmClient.generateEmbedding(text);
        } catch (Exception ignored) {
            return new float[0];
        }
    }

    private double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || b.length == 0 || a.length != b.length) {
            return 0d;
        }
        double dot = 0d;
        double na = 0d;
        double nb = 0d;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        if (na == 0d || nb == 0d) {
            return 0d;
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    private double round2(double value) {
        return Math.round(value * 100.0d) / 100.0d;
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String asString(Object value, String defaultValue) {
        String str = asString(value).trim();
        return str.isBlank() ? defaultValue : str;
    }

    private record Match(String sourcePath, double score, String reason) {}
}
