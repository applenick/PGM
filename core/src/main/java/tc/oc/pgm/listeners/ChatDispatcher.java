package tc.oc.pgm.listeners;

import static net.kyori.adventure.identity.Identity.identity;
import static net.kyori.adventure.key.Key.key;
import static net.kyori.adventure.sound.Sound.sound;
import static net.kyori.adventure.text.Component.space;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.translatable;
import static tc.oc.pgm.util.text.PlayerComponent.player;
import static tc.oc.pgm.util.text.TextTranslations.translate;

import app.ashcon.intake.Command;
import app.ashcon.intake.parametric.annotation.Text;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import tc.oc.pgm.api.PGM;
import tc.oc.pgm.api.Permissions;
import tc.oc.pgm.api.integration.Integration;
import tc.oc.pgm.api.match.Match;
import tc.oc.pgm.api.match.MatchManager;
import tc.oc.pgm.api.party.Party;
import tc.oc.pgm.api.player.MatchPlayer;
import tc.oc.pgm.api.player.event.MatchPlayerChatEvent;
import tc.oc.pgm.api.setting.SettingKey;
import tc.oc.pgm.api.setting.SettingValue;
import tc.oc.pgm.ffa.Tribute;
import tc.oc.pgm.util.Audience;
import tc.oc.pgm.util.StringUtils;
import tc.oc.pgm.util.bukkit.BukkitUtils;
import tc.oc.pgm.util.bukkit.OnlinePlayerMapAdapter;
import tc.oc.pgm.util.named.NameStyle;
import tc.oc.pgm.util.text.PlayerComponentProvider;
import tc.oc.pgm.util.text.TextTranslations;

public class ChatDispatcher implements Listener {

  private static ChatDispatcher INSTANCE = new ChatDispatcher();

  public static ChatDispatcher get() {
    return INSTANCE; // FIXME: no one should need to statically access ChatDispatcher, but community
    // does this a lot
  }

  private final MatchManager manager;
  private final OnlinePlayerMapAdapter<UUID> lastMessagedBy;

  public static final TextComponent ADMIN_CHAT_PREFIX =
      text()
          .append(text("[", NamedTextColor.WHITE))
          .append(text("A", NamedTextColor.GOLD))
          .append(text("] ", NamedTextColor.WHITE))
          .build();

  private static final Sound DM_SOUND = sound(key("random.orb"), Sound.Source.MASTER, 1f, 1.2f);
  private static final Sound AC_SOUND = sound(key("random.orb"), Sound.Source.MASTER, 1f, 0.7f);

  private static final String GLOBAL_SYMBOL = "!";
  private static final String DM_SYMBOL = "@";
  private static final String ADMIN_CHAT_SYMBOL = "$";

  private static final String GLOBAL_FORMAT = "<%s>: %s";
  private static final String PREFIX_FORMAT = "%s: %s";
  private static final String AC_FORMAT =
      TextTranslations.translateLegacy(ADMIN_CHAT_PREFIX, null) + PREFIX_FORMAT;

  private static final Predicate<MatchPlayer> AC_FILTER =
      viewer -> viewer.getBukkit().hasPermission(Permissions.ADMINCHAT);

  public ChatDispatcher() {
    this.manager = PGM.get().getMatchManager();
    this.lastMessagedBy = new OnlinePlayerMapAdapter<>(PGM.get());
    PGM.get().getServer().getPluginManager().registerEvents(this, PGM.get());
  }

  @Command(
      aliases = {"g", "all"},
      desc = "Send a message to everyone",
      usage = "[message]")
  public void sendGlobal(Match match, MatchPlayer sender, @Nullable @Text String message) {
    if (sender != null && Integration.isVanished(sender.getBukkit())) {
      sendAdmin(match, sender, message);
      return;
    }

    send(
        match,
        sender,
        message,
        GLOBAL_FORMAT,
        null,
        viewer -> true,
        SettingValue.CHAT_GLOBAL,
        Channel.GLOBAL);
  }

  @Command(
      aliases = {"t"},
      desc = "Send a message to your team",
      usage = "[message]")
  public void sendTeam(Match match, MatchPlayer sender, @Nullable @Text String message) {
    if (sender != null && Integration.isVanished(sender.getBukkit())) {
      sendAdmin(match, sender, message);
      return;
    }

    final Party party = sender == null ? match.getDefaultParty() : sender.getParty();

    // No team chat when playing free-for-all or match end, default to global chat
    if (party instanceof Tribute || match.isFinished()) {
      sendGlobal(match, sender, message);
      return;
    }

    send(
        match,
        sender,
        message,
        TextTranslations.translateLegacy(party.getChatPrefix(), null) + PREFIX_FORMAT,
        party.getChatPrefix(),
        viewer ->
            party.equals(viewer.getParty())
                || (viewer.isObserving()
                    && viewer.getBukkit().hasPermission(Permissions.ADMINCHAT)),
        SettingValue.CHAT_TEAM,
        Channel.TEAM);
  }

  @Command(
      aliases = {"a"},
      desc = "Send a message to operators",
      usage = "[message]",
      perms = Permissions.ADMINCHAT)
  public void sendAdmin(Match match, MatchPlayer sender, @Nullable @Text String message) {
    // If a player managed to send a default message without permissions, reset their chat channel
    if (sender != null && !sender.getBukkit().hasPermission(Permissions.ADMINCHAT)) {
      sender.getSettings().resetValue(SettingKey.CHAT);
      SettingKey.CHAT.update(sender);
      sender.sendWarning(translatable("misc.noPermission"));
      return;
    }

    send(
        match,
        sender,
        message != null ? BukkitUtils.colorize(message) : null,
        AC_FORMAT,
        ADMIN_CHAT_PREFIX,
        AC_FILTER,
        SettingValue.CHAT_ADMIN,
        Channel.ADMIN);

    // Play sounds for admin chat
    if (message != null) {
      match.getPlayers().stream()
          .filter(AC_FILTER) // Initial filter
          .filter(viewer -> !viewer.equals(sender)) // Don't play sound for sender
          .forEach(pl -> playSound(pl, AC_SOUND));
    }
  }

  @Command(
      aliases = {"msg", "tell", "pm", "dm"},
      desc = "Send a direct message to a player",
      usage = "[player] [message]")
  public void sendDirect(Match match, MatchPlayer sender, Player receiver, @Text String message) {
    if (sender == null) return;

    if (Integration.isVanished(sender.getBukkit())) {
      sender.sendWarning(translatable("vanish.chat.deny"));
      return;
    }

    // Sender muted, don't allow messages unless to staff
    if (Integration.isMuted(sender.getBukkit()) && !receiver.hasPermission(Permissions.STAFF)) {
      sender.sendWarning(
          translatable(
              "moderation.mute.message",
              NamedTextColor.GRAY,
              text(Integration.getMuteReason(sender.getBukkit()), NamedTextColor.RED)));
      return;
    }

    MatchPlayer matchReceiver = manager.getPlayer(receiver);
    if (matchReceiver != null) {

      // Vanish Check - Don't allow messages to vanished
      if (Integration.isVanished(receiver)) {
        sender.sendWarning(translatable("command.playerNotFound"));
        return;
      }

      // Sender setting check
      SettingValue senderOptions = sender.getSettings().getValue(SettingKey.MESSAGE);
      if (senderOptions.equals(SettingValue.MESSAGE_FRIEND)
          && !Integration.isFriend(sender.getBukkit(), receiver)) {
        sender.sendWarning(translatable("command.message.friendsOnly", matchReceiver.getName()));
        return;
      }

      if (senderOptions.equals(SettingValue.MESSAGE_OFF)) {
        sender.sendWarning(
            translatable(
                "command.message.disabled", text("/toggle message", NamedTextColor.GREEN)));
        return;
      }

      // Reciever Setting Check
      SettingValue option = matchReceiver.getSettings().getValue(SettingKey.MESSAGE);
      if (!sender.getBukkit().hasPermission(Permissions.STAFF)) {
        if ((option.equals(SettingValue.MESSAGE_FRIEND)
                && !Integration.isFriend(sender.getBukkit(), receiver))
            || option.equals(SettingValue.MESSAGE_OFF)) {
          Component blocked = translatable("command.message.blocked", matchReceiver.getName());
          sender.sendWarning(blocked);
          return;
        }
      }

      if (Integration.isMuted(matchReceiver.getBukkit())
          && !sender.getBukkit().hasPermission(Permissions.STAFF)) {
        Component muted =
            translatable("moderation.mute.target", matchReceiver.getName(NameStyle.CONCISE));
        sender.sendWarning(muted);
        return; // Only staff can message muted players
      }

      playSound(matchReceiver, DM_SOUND);
    }

    if (sender != null) {
      lastMessagedBy.put(receiver, sender.getId());
    }

    // Send message to receiver
    send(
        match,
        sender,
        message,
        formatPrivateMessage("misc.from", matchReceiver.getBukkit()),
        text()
            .append(translatable("misc.from", NamedTextColor.GRAY, TextDecoration.ITALIC))
            .append(space())
            .build(),
        viewer -> viewer.getBukkit().equals(receiver),
        null,
        Channel.PRIVATE);

    // Send message to the sender
    send(
        match,
        manager.getPlayer(receiver), // Allow for cross-match messages
        message,
        formatPrivateMessage("misc.to", sender.getBukkit()),
        text()
            .append(translatable("misc.to", NamedTextColor.GRAY, TextDecoration.ITALIC))
            .append(space())
            .build(),
        viewer -> viewer.getBukkit().equals(sender.getBukkit()),
        null,
        Channel.PRIVATE);
  }

  private String formatPrivateMessage(String key, CommandSender viewer) {
    Component action =
        translatable(key, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, true);
    return TextTranslations.translateLegacy(action, viewer) + " " + PREFIX_FORMAT;
  }

  @Command(
      aliases = {"reply", "r"},
      desc = "Reply to a direct message",
      usage = "[message]")
  public void sendReply(Match match, Audience audience, MatchPlayer sender, @Text String message) {
    if (sender == null) return;
    final MatchPlayer receiver = manager.getPlayer(lastMessagedBy.get(sender.getBukkit()));
    if (receiver == null) {
      audience.sendWarning(translatable("command.message.noReply", text("/msg")));
      return;
    }

    sendDirect(match, sender, receiver.getBukkit(), message);
  }

  @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
  public void onChat(AsyncPlayerChatEvent event) {
    if (CHAT_EVENT_CACHE.getIfPresent(event) == null) {
      event.setCancelled(true);
    } else {
      CHAT_EVENT_CACHE.invalidate(event);
      return;
    }

    final MatchPlayer player = manager.getPlayer(event.getPlayer());
    if (player != null) {
      final String message = event.getMessage();

      if (message.startsWith(GLOBAL_SYMBOL)) {
        sendGlobal(player.getMatch(), player, message.substring(1));
      } else if (message.startsWith(DM_SYMBOL) && message.contains(" ")) {
        final String target = message.substring(1, message.indexOf(" "));
        final MatchPlayer receiver =
            getApproximatePlayer(player.getMatch(), target, player.getBukkit());
        if (receiver == null) {
          player.sendWarning(translatable("chat.message.unknownTarget", text(target)));
        } else {
          sendDirect(
              player.getMatch(),
              player,
              receiver.getBukkit(),
              message.replace(target, "").substring(1));
        }
      } else if (message.startsWith(ADMIN_CHAT_SYMBOL)
          && player.getBukkit().hasPermission(Permissions.ADMINCHAT)) {
        sendAdmin(player.getMatch(), player, event.getMessage().substring(1));
      } else {
        sendDefault(player.getMatch(), player, event.getMessage());
      }
    }
  }

  public void sendDefault(Match match, MatchPlayer sender, String message) {
    switch (sender == null
        ? SettingValue.CHAT_GLOBAL
        : sender.getSettings().getValue(SettingKey.CHAT)) {
      case CHAT_TEAM:
        sendTeam(match, sender, message);
        return;
      case CHAT_ADMIN:
        sendAdmin(match, sender, message);
        return;
      default:
        sendGlobal(match, sender, message);
    }
  }

  public void playSound(MatchPlayer player, Sound sound) {
    SettingValue value = player.getSettings().getValue(SettingKey.SOUNDS);
    if ((sound.equals(AC_SOUND) && value.equals(SettingValue.SOUNDS_ALL))
        || (sound.equals(DM_SOUND) && !value.equals(SettingValue.SOUNDS_NONE))) {
      player.playSound(sound);
    }
  }

  private static final Cache<AsyncPlayerChatEvent, Boolean> CHAT_EVENT_CACHE =
      CacheBuilder.newBuilder().weakKeys().expireAfterWrite(15, TimeUnit.SECONDS).build();

  public void send(
      Match match,
      MatchPlayer sender,
      @Nullable String text,
      String format,
      @Nullable Component prefix,
      Predicate<MatchPlayer> filter,
      @Nullable SettingValue type,
      Channel channel) {
    // When a message is empty, this indicates the player wants to change their default chat channel
    if ((text == null || text.isEmpty()) && sender != null) {
      // FIXME: there should be a better way to do this
      PGM.get()
          .getExecutor()
          .schedule(
              () ->
                  sender
                      .getBukkit()
                      .performCommand("set " + SettingKey.CHAT + " " + type.getName()),
              50,
              TimeUnit.MILLISECONDS); // Run sync to stop console spam
      return;
    }

    final String message = text.trim();

    if (sender != null) {
      PGM.get()
          .getAsyncExecutor()
          .execute(
              () -> {
                final AsyncPlayerChatEvent event =
                    new AsyncPlayerChatEvent(
                        false,
                        sender.getBukkit(),
                        message,
                        match.getPlayers().stream()
                            .filter(filter)
                            .map(MatchPlayer::getBukkit)
                            .collect(Collectors.toSet()));
                event.setFormat(format);
                CHAT_EVENT_CACHE.put(event, true);
                match.callEvent(event);
                match.callEvent(new MatchPlayerChatEvent(sender, text(message), channel));

                if (event.isCancelled()) {
                  return;
                }

                event.getRecipients().stream()
                    .forEach(
                        player -> {
                          Audience audience = Audience.get(player);
                          audience.sendMessage(
                              identity(sender.getId()),
                              getChatFormat(
                                  prefix, player(sender.getBukkit(), NameStyle.VERBOSE), message));
                        });
              });
      return;
    }
    match.getPlayers().stream()
        .filter(filter)
        .forEach(
            player ->
                player.sendMessage(
                    text(
                        String.format(
                            format,
                            translate(
                                PlayerComponentProvider.CONSOLE,
                                TextTranslations.getLocale(player.getBukkit())),
                            message))));
  }

  private MatchPlayer getApproximatePlayer(Match match, String query, CommandSender sender) {
    return StringUtils.bestFuzzyMatch(
        query,
        match.getPlayers().stream()
            .collect(Collectors.toMap(player -> player.getBukkit().getName(), Function.identity())),
        0.75);
  }

  public static void broadcastAdminChatMessage(Component message, Match match) {
    broadcastAdminChatMessage(message, match, Optional.empty());
  }

  public static void broadcastAdminChatMessage(
      Component message, Match match, Optional<Sound> sound) {
    TextComponent.Builder formatted = text().append(ADMIN_CHAT_PREFIX).append(message);
    List<MatchPlayer> staffPlayers =
        match.getPlayers().stream().filter(AC_FILTER).collect(Collectors.toList());
    Audience staffAudience = Audience.get(staffPlayers);
    staffAudience.sendMessage(formatted);
    sound.ifPresent(
        alertSound -> {
          staffPlayers.stream()
              .filter(ChatDispatcher::canPlaySound)
              .forEach(mp -> mp.playSound(alertSound));
        });
    Audience.console().sendMessage(formatted);
  }

  private static boolean canPlaySound(MatchPlayer viewer) {
    return viewer.getSettings().getValue(SettingKey.SOUNDS).equals(SettingValue.SOUNDS_ALL);
  }

  private Component getChatFormat(@Nullable Component prefix, Component name, String message) {
    Component msg = text(message != null ? message : "");
    if (prefix == null)
      return text()
          .append(text("<", NamedTextColor.WHITE))
          .append(name)
          .append(text(">: ", NamedTextColor.WHITE))
          .append(msg)
          .build();
    return text()
        .append(prefix)
        .append(name)
        .append(text(": ", NamedTextColor.WHITE))
        .append(msg)
        .build();
  }

  public static enum Channel {
    GLOBAL,
    TEAM,
    PRIVATE,
    ADMIN;
  }
}
