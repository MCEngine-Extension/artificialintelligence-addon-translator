package io.github.mcengine.extension.addon.translator.translate;

import com.google.gson.JsonObject;
import io.github.mcengine.api.artificialintelligence.MCEngineArtificialIntelligenceApi;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Wraps {@link MCEngineArtificialIntelligenceApi} calls and caches results.
 * Translates once per target language per message.
 */
public class TranslationService {

    /** Owning plugin used to log warnings on failures. */
    private final Plugin plugin;
    /** AI API facade used to perform translations. */
    private final MCEngineArtificialIntelligenceApi ai;
    /** Local translation cache (message+lang -> text). */
    private final TranslationCache cache;
    /** AI platform identifier (e.g., openai, deepseek). */
    private final String platform;
    /** AI model name on the selected platform. */
    private final String model;
    /** Token source type: "server" or "player". */
    private final String tokenType;
    /** System prompt describing translator behavior. */
    private final String systemPrompt;

    public TranslationService(Plugin plugin,
                              MCEngineArtificialIntelligenceApi ai,
                              TranslationCache cache,
                              String platform,
                              String model,
                              String tokenType,
                              String systemPrompt) {
        this.plugin = plugin;
        this.ai = ai;
        this.cache = cache;
        this.platform = platform;
        this.model = model;
        this.tokenType = tokenType;
        this.systemPrompt = systemPrompt;
    }

    /**
     * Translates {@code message} once per language in {@code targetLangs}, using cache to dedupe.
     *
     * @return future mapping target language -> translated string.
     */
    public CompletableFuture<Map<String, String>> translateOncePerLanguage(String message, Set<String> targetLangs) {
        Map<String, String> result = new ConcurrentHashMap<>();
        List<CompletableFuture<Void>> tasks = new ArrayList<>();

        for (String lang : targetLangs) {
            String cached = cache.get(message, lang);
            if (cached != null) { result.put(lang, cached); continue; }

            CompletableFuture<Void> fut = CompletableFuture.runAsync(() -> {
                try {
                    String userPrompt = "Target language code: " + lang + "\nText:\n" + message;
                    JsonObject response;
                    if ("server".equalsIgnoreCase(tokenType)) {
                        response = ai.getResponse(platform, model, systemPrompt, userPrompt);
                    } else {
                        Player any = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
                        response = ai.getResponse(platform, model, systemPrompt, userPrompt);
                    }
                    String translated = ai.getCompletionContent(response);
                    if (translated == null) translated = "";
                    translated = translated.trim();
                    if (translated.startsWith("\"") && translated.endsWith("\"") && translated.length() > 1) {
                        translated = translated.substring(1, translated.length() - 1).trim();
                    }
                    cache.put(message, lang, translated);
                    result.put(lang, translated);
                } catch (Exception ex) {
                    plugin.getLogger().warning("Failed to translate to " + lang + ": " + ex.getMessage());
                }
            });
            tasks.add(fut);
        }
        return CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0])).thenApply(v -> result);
    }
}
