package tc.oc.pgm.api.integration;

import java.util.concurrent.CompletableFuture;
import org.bukkit.entity.Player;
import tc.oc.pgm.util.translation.Translation;

public interface TranslationIntegration {

  CompletableFuture<Translation> translate(Player sender, String message);
}
