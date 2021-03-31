package tc.oc.pgm.api.event;

import org.bukkit.Skin;
import org.bukkit.event.HandlerList;
import tc.oc.pgm.api.player.MatchPlayer;
import tc.oc.pgm.api.player.event.MatchPlayerEvent;

/**
 * Called when a players skin is changed. Used in the steve skin module to update the tab list items
 * inline with players.
 */
public class PlayerSkinChangeEvent extends MatchPlayerEvent {

  private final Skin skin;

  public PlayerSkinChangeEvent(MatchPlayer player, Skin skin) {
    super(player);
    this.skin = skin;
  }

  public Skin getSkin() {
    return skin;
  }

  private static final HandlerList handlers = new HandlerList();

  public static HandlerList getHandlerList() {
    return handlers;
  }

  @Override
  public HandlerList getHandlers() {
    return handlers;
  }
}
