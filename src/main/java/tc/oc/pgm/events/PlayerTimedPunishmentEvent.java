package tc.oc.pgm.events;

import static com.google.common.base.Preconditions.checkNotNull;

import java.time.Duration;
import java.time.Instant;
import org.bukkit.command.CommandSender;
import tc.oc.pgm.api.player.MatchPlayer;
import tc.oc.pgm.community.ModerationCommands.PunishmentType;

/** Called when a punishment that expires after a duration of time is created * */
public class PlayerTimedPunishmentEvent extends PlayerPunishmentEvent {

  private final Duration time;

  public PlayerTimedPunishmentEvent(
      CommandSender sender,
      MatchPlayer player,
      PunishmentType punishment,
      String reason,
      boolean silent,
      Duration expires) {
    super(sender, player, punishment, reason, silent);
    this.time = checkNotNull(expires);
  }

  public Duration getPunishmentLength() {
    return time;
  }

  public Instant getExpiryDate() {
    return Instant.now().plus(time);
  }
}
