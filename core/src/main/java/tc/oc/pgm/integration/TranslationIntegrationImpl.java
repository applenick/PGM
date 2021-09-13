package tc.oc.pgm.integration;

import java.util.concurrent.CompletableFuture;
import org.bukkit.entity.Player;
import tc.oc.pgm.api.integration.TranslationIntegration;
import tc.oc.pgm.util.translation.Translation;

public class TranslationIntegrationImpl implements TranslationIntegration {

  @Override
  public CompletableFuture<Translation> translate(Player sender, String message) {
    return CompletableFuture.completedFuture(new Translation(sender, message));
  }
}
