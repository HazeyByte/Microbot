package net.runelite.client.plugins.microbot.ui.search;

import net.runelite.client.plugins.config.SearchablePlugin;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class MicrobotPluginSearch
{
    private static final int RANK_NAME_PREFIX = 0;
    private static final int RANK_NAME_WORD = 1;
    private static final int RANK_OTHER = 2;

    private static final class Row<T extends SearchablePlugin>
    {
        final T plugin;
        final String name;
        final List<String> keywords;

        Row(T plugin)
        {
            this.plugin = plugin;
            name = MicrobotPluginNames.sortKey(plugin.getSearchableName());
            List<String> rawKeywords = plugin.getKeywords();
            keywords = rawKeywords == null ? Collections.emptyList() : rawKeywords.stream()
                    .map(MicrobotPluginNames::searchText)
                    .collect(Collectors.toList());
        }

        boolean matches(List<String> tokens)
        {
            return tokens.stream().allMatch(token -> name.contains(token)
                    || keywords.stream().anyMatch(keyword -> keyword.contains(token)));
        }

        int rank(String query, List<String> tokens)
        {
            if (name.startsWith(query))
            {
                return RANK_NAME_PREFIX;
            }

            String words = " " + name;
            return tokens.stream().allMatch(token -> words.contains(" " + token)) ? RANK_NAME_WORD : RANK_OTHER;
        }
    }

    public static <T extends SearchablePlugin> List<T> search(Collection<T> searchablePlugins, String query)
    {
        String trimmed = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<String> tokens = trimmed.isEmpty() ? Collections.emptyList() : Arrays.asList(trimmed.split("\\s+"));
        // Ranked on the same single-spaced words it is matched on, so "aio  magic" ranks like "aio magic".
        String q = String.join(" ", tokens);

        return searchablePlugins.stream()
                .map(Row::new)
                .filter(row -> row.matches(tokens))
                .sorted(Comparator.<Row<T>>comparingInt(row -> row.rank(q, tokens))
                        .thenComparing(row -> row.plugin.isPinned(), Comparator.reverseOrder())
                        .thenComparing(row -> row.name))
                .map(row -> row.plugin)
                .collect(Collectors.toList());
    }
}
