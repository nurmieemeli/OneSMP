package gg.nurmi.message.help;

import org.bukkit.Material;

import java.util.List;

public record HelpCategory(String key, String displayName, Material icon, String description, List<HelpArticle> articles) {

    public HelpArticle article(String articleKey) {
        return articles.stream()
                .filter(article -> article.key().equalsIgnoreCase(articleKey))
                .findFirst()
                .orElse(null);
    }

    public boolean hasDescription() {
        return description != null && !description.isBlank();
    }
}
