package tc.oc.pgm.integration;

import javax.annotation.Nullable;
import org.bukkit.entity.Player;
import tc.oc.pgm.api.integration.PunishmentIntegration;

public class PunishmentIntegrationImpl implements PunishmentIntegration {

  @Override
  public boolean isMuted(Player player) {
    return false;
  }

  @Override
  public @Nullable String getMuteReason(Player player) {
    return "";
  }

  @Override
  public boolean isHidden(Player player) {
    return false;
  }
}