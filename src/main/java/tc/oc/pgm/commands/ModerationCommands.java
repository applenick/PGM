package tc.oc.pgm.commands;

import app.ashcon.intake.Command;
import app.ashcon.intake.CommandException;
import app.ashcon.intake.parametric.annotation.Switch;
import app.ashcon.intake.parametric.annotation.Text;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Lists;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import tc.oc.component.Component;
import tc.oc.component.types.PersonalizedText;
import tc.oc.component.types.PersonalizedTranslatable;
import tc.oc.named.NameStyle;
import tc.oc.pgm.Config;
import tc.oc.pgm.api.Permissions;
import tc.oc.pgm.api.chat.Audience;
import tc.oc.pgm.api.chat.Sound;
import tc.oc.pgm.api.match.Match;
import tc.oc.pgm.api.player.MatchPlayer;
import tc.oc.pgm.api.setting.SettingKey;
import tc.oc.pgm.api.setting.SettingValue;
import tc.oc.pgm.events.PlayerPunishmentEvent;
import tc.oc.pgm.events.PlayerReportEvent;
import tc.oc.pgm.events.PlayerTimedPunishmentEvent;
import tc.oc.pgm.listeners.ChatDispatcher;
import tc.oc.util.components.ComponentUtils;
import tc.oc.util.components.Components;
import tc.oc.util.components.PeriodFormats;

public class ModerationCommands {

  private static final Sound WARN_SOUND = new Sound("mob.enderdragon.growl", 1f, 1f);
  private static final Component WARN_SYMBOL =
      new PersonalizedText(" \u26a0 ").color(ChatColor.YELLOW);
  private static final Component BROADCAST_DIV =
      new PersonalizedText(" \u00BB ").color(ChatColor.GOLD);

  private static final Component CONSOLE_NAME =
      new PersonalizedTranslatable("console")
          .getPersonalizedText()
          .color(ChatColor.DARK_AQUA)
          .italic(true);

  private static final int REPORT_COOLDOWN_SECONDS = 15;

  private final Cache<UUID, Instant> LAST_REPORT_SENT =
      CacheBuilder.newBuilder().expireAfterWrite(REPORT_COOLDOWN_SECONDS, TimeUnit.SECONDS).build();

  private static final Sound REPORT_NOTIFY_SOUND = new Sound("random.pop", 1f, 1.2f);

  private final ChatDispatcher chat;

  public ModerationCommands(ChatDispatcher chat) {
    this.chat = chat;
  }

  @Command(
      aliases = {"report"},
      usage = "<player> <reason>",
      desc = "Report a player who is breaking the rules")
  public void report(
      CommandSender commandSender,
      MatchPlayer matchPlayer,
      Match match,
      Player player,
      @Text String reason)
      throws CommandException {
    if (!commandSender.hasPermission(Permissions.STAFF) && commandSender instanceof Player) {
      // Check for cooldown
      Instant lastReport = LAST_REPORT_SENT.getIfPresent(matchPlayer.getId());
      if (lastReport != null) {
        Duration timeSinceReport = Duration.between(lastReport, Instant.now());
        long secondsRemaining = REPORT_COOLDOWN_SECONDS - timeSinceReport.getSeconds();
        if (secondsRemaining > 0) {
          Component secondsComponent = new PersonalizedText(Long.toString(secondsRemaining));
          Component secondsLeftComponent =
              new PersonalizedTranslatable(
                      secondsRemaining != 1
                          ? "countdown.pluralCompound"
                          : "countdown.singularCompound",
                      secondsComponent)
                  .getPersonalizedText()
                  .color(ChatColor.AQUA);
          commandSender.sendMessage(
              new PersonalizedTranslatable("command.cooldown", secondsLeftComponent)
                  .getPersonalizedText()
                  .color(ChatColor.RED));
          return;
        }
      } else {
        // Player has no cooldown, so add one
        LAST_REPORT_SENT.put(matchPlayer.getId(), Instant.now());
      }
    }

    MatchPlayer accused = match.getPlayer(player);
    PlayerReportEvent event = new PlayerReportEvent(commandSender, accused, reason);
    match.callEvent(event);

    if (event.isCancelled()) {
      if (event.getCancelMessage() != null) {
        commandSender.sendMessage(event.getCancelMessage());
      }
      return;
    }

    commandSender.sendMessage(
        new PersonalizedText(
            new PersonalizedText(new PersonalizedTranslatable("misc.thankYou"), ChatColor.GREEN),
            new PersonalizedText(" "),
            new PersonalizedText(
                new PersonalizedTranslatable("command.report.acknowledge"), ChatColor.GOLD)));

    final Component component =
        new PersonalizedTranslatable(
            "command.report.notify",
            matchPlayer == null
                ? new PersonalizedText("Console", ChatColor.AQUA, ChatColor.ITALIC)
                : matchPlayer.getStyledName(NameStyle.FANCY),
            accused.getStyledName(NameStyle.FANCY),
            new PersonalizedText(reason.trim(), ChatColor.WHITE));

    final Component prefixedComponent =
        new PersonalizedText(
            new PersonalizedText("["),
            new PersonalizedText("A", ChatColor.GOLD),
            new PersonalizedText("] "),
            new PersonalizedText(component, ChatColor.YELLOW));

    match.getPlayers().stream()
        .filter(viewer -> viewer.getBukkit().hasPermission(Permissions.ADMINCHAT))
        .forEach(
            viewer -> {
              // Play sound for viewers of reports
              if (viewer.getSettings().getValue(SettingKey.SOUNDS).equals(SettingValue.SOUNDS_ON)) {
                viewer.playSound(REPORT_NOTIFY_SOUND);
              }
              viewer.sendMessage(prefixedComponent);
            });
    Audience.get(Bukkit.getConsoleSender()).sendMessage(component);
  }

  @Command(
      aliases = {"staff", "mods", "admins"},
      desc = "List the online staff members")
  public void staff(CommandSender sender, Match match) {
    // List of online staff
    List<Component> onlineStaff =
        match.getPlayers().stream()
            .filter(player -> player.getBukkit().hasPermission(Permissions.STAFF))
            .map(player -> player.getStyledName(NameStyle.FANCY))
            .collect(Collectors.toList());

    // FORMAT: Online Staff ({count}): {names}
    Component staffCount =
        new PersonalizedText(Integer.toString(onlineStaff.size()))
            .color(onlineStaff.isEmpty() ? ChatColor.RED : ChatColor.AQUA);

    Component content =
        onlineStaff.isEmpty()
            ? new PersonalizedTranslatable("moderation.staff.empty")
                .getPersonalizedText()
                .color(ChatColor.RED)
            : new Component(
                Components.join(new PersonalizedText(", ").color(ChatColor.GRAY), onlineStaff));

    Component staff =
        new PersonalizedTranslatable("moderation.staff.name", staffCount, content)
            .getPersonalizedText()
            .color(ChatColor.GRAY);

    // Send message
    sender.sendMessage(staff);
  }

  @Command(
      aliases = {"mute", "m"},
      usage = "<player> <reason> -s (silent) -w (warn)",
      desc = "Mute a player",
      perms = Permissions.MUTE)
  public void mute(
      CommandSender sender,
      Player target,
      Match match,
      @Text String reason,
      @Switch('s') boolean silent,
      @Switch('w') boolean warn) {
    MatchPlayer targetMatchPlayer = match.getPlayer(target);

    // if -w flag, also warn the player but don't broadcast warning
    if (warn) {
      warn(sender, target, match, reason, true);
    }

    PlayerPunishmentEvent event =
        punish(PunishmentType.MUTE, targetMatchPlayer, sender, reason, silent);
    if (!event.isCancelled()) {
      broadcastPunishment(event);
      chat.addMuted(targetMatchPlayer);
    } else if (event.getCancelMessage() != null) {
      sender.sendMessage(event.getCancelMessage());
    }
  }

  @Command(
      aliases = {"unmute", "um"},
      usage = "<player>",
      desc = "Unmute a player",
      perms = Permissions.MUTE)
  public void unMute(CommandSender sender, Player target, Match match) {
    MatchPlayer targetMatchPlayer = match.getPlayer(target);
    if (chat.isMuted(targetMatchPlayer)) {
      chat.removeMuted(targetMatchPlayer);

      targetMatchPlayer.sendMessage(
          new PersonalizedTranslatable("moderation.unmute.target")
              .getPersonalizedText()
              .color(ChatColor.GREEN));

      sender.sendMessage(
          new PersonalizedTranslatable(
                  "moderation.unmute.sender", targetMatchPlayer.getStyledName(NameStyle.FANCY))
              .color(ChatColor.GRAY));
    } else {
      sender.sendMessage(
          new PersonalizedTranslatable(
                  "moderation.unmute.none", targetMatchPlayer.getStyledName(NameStyle.FANCY))
              .getPersonalizedText()
              .color(ChatColor.RED));
    }
  }

  @Command(
      aliases = {"warn", "w"},
      usage = "<player> <reason> -s (silent)",
      desc = "Warn a player for bad behavior",
      perms = Permissions.WARN)
  public void warn(
      CommandSender sender,
      Player target,
      Match match,
      @Text String reason,
      @Switch('s') boolean silent) {
    MatchPlayer targetMatchPlayer = match.getPlayer(target);
    PlayerPunishmentEvent event =
        punish(PunishmentType.WARN, targetMatchPlayer, sender, reason, silent);
    if (!event.isCancelled()) {
      broadcastPunishment(event);
      sendWarning(targetMatchPlayer, reason);
    } else if (event.getCancelMessage() != null) {
      sender.sendMessage(event.getCancelMessage());
    }
  }

  @Command(
      aliases = {"kick", "k"},
      usage = "<player> <reason> -s (silent)",
      desc = "Kick a player from the server",
      perms = Permissions.KICK)
  public void kick(
      CommandSender sender,
      Player target,
      Match match,
      @Text String reason,
      @Switch('s') boolean silent) {
    MatchPlayer targetMatchPlayer = match.getPlayer(target);
    PlayerPunishmentEvent event =
        punish(PunishmentType.KICK, targetMatchPlayer, sender, reason, silent);
    if (!event.isCancelled()) {
      broadcastPunishment(event);
      target.kickPlayer(
          formatPunishmentScreen(
              PunishmentType.KICK, formatPunisherName(sender, match), reason, null));
    } else if (event.getCancelMessage() != null) {
      sender.sendMessage(event.getCancelMessage());
    }
  }

  @Command(
      aliases = {"ban", "permban", "pb"},
      usage = "<player> <reason> -s (silent)",
      desc = "Ban a player from the server forever",
      perms = Permissions.BAN)
  public void ban(
      CommandSender sender,
      Player target,
      Match match,
      @Text String reason,
      @Switch('s') boolean silent) {
    MatchPlayer targetMatchPlayer = match.getPlayer(target);
    PlayerPunishmentEvent event =
        punish(PunishmentType.BAN, targetMatchPlayer, sender, reason, silent);
    if (!event.isCancelled()) {
      broadcastPunishment(event);
      banPlayer(event, null);
      target.kickPlayer(
          formatPunishmentScreen(
              PunishmentType.BAN, formatPunisherName(sender, match), reason, null));
    } else if (event.getCancelMessage() != null) {
      sender.sendMessage(event.getCancelMessage());
    }
  }

  @Command(
      aliases = {"tempban", "tban", "tb"},
      usage = "<player> <time> <reason> -s (silent)",
      desc = "Ban a player from the server for a period of time",
      perms = Permissions.BAN)
  public void tempBan(
      CommandSender sender,
      Player target,
      Match match,
      Duration banLength,
      @Text String reason,
      @Switch('s') boolean silent) {
    MatchPlayer targetMatchPlayer = match.getPlayer(target);
    PlayerTimedPunishmentEvent event =
        new PlayerTimedPunishmentEvent(
            sender, targetMatchPlayer, PunishmentType.TEMP_BAN, reason, silent, banLength);
    match.callEvent(event);
    if (!event.isCancelled()) {
      broadcastPunishment(event);
      banPlayer(event, event.getExpiryDate());
      target.kickPlayer(
          formatPunishmentScreen(
              PunishmentType.BAN, formatPunisherName(sender, match), reason, banLength));
    } else if (event.getCancelMessage() != null) {
      sender.sendMessage(event.getCancelMessage());
    }
  }

  private PlayerPunishmentEvent punish(
      PunishmentType type,
      MatchPlayer target,
      CommandSender issuer,
      String reason,
      boolean silent) {
    PlayerPunishmentEvent event = new PlayerPunishmentEvent(issuer, target, type, reason, silent);
    target.getMatch().callEvent(event);

    return event;
  }

  public static enum PunishmentType {
    MUTE(false),
    WARN(false),
    KICK(true),
    BAN(true),
    TEMP_BAN(true);

    private String PREFIX_TRANSLATE_KEY = "moderation.type.";
    private String SCREEN_TRANSLATE_KEY = "moderation.screen.";

    private final boolean screen;

    PunishmentType(boolean screen) {
      this.screen = screen;
    }

    public Component getPunishmentPrefix() {
      return new PersonalizedTranslatable(PREFIX_TRANSLATE_KEY + name().toLowerCase())
          .getPersonalizedText()
          .color(ChatColor.RED);
    }

    public Component getScreenComponent(Component reason) {
      if (!screen) return Components.blank();
      return new PersonalizedTranslatable(SCREEN_TRANSLATE_KEY + name().toLowerCase(), reason)
          .getPersonalizedText()
          .color(ChatColor.GOLD);
    }
  }

  /*
   * Format Punisher Name
   */
  public static Component formatPunisherName(CommandSender sender, Match match) {
    if (sender != null && sender instanceof Player) {
      MatchPlayer matchPlayer = match.getPlayer((Player) sender);
      if (matchPlayer != null) return matchPlayer.getStyledName(NameStyle.FANCY);
    }
    return CONSOLE_NAME;
  }

  /*
   * Format Reason
   */
  public static Component formatPunishmentReason(String reason) {
    return new PersonalizedText(reason).color(ChatColor.RED);
  }

  /*
   * Formatting of Kick Screens (KICK/BAN/TEMPBAN)
   */
  public static String formatPunishmentScreen(
      PunishmentType type, Component punisher, String reason, @Nullable Duration expires) {
    List<Component> lines = Lists.newArrayList();

    Component header =
        new PersonalizedText(
            ComponentUtils.horizontalLineHeading(
                Config.Moderation.getServerName(), ChatColor.DARK_GRAY));

    Component footer =
        new PersonalizedText(
            ComponentUtils.horizontalLine(ChatColor.DARK_GRAY, ComponentUtils.MAX_CHAT_WIDTH));

    Component rules = new PersonalizedText(Config.Moderation.getRulesLink()).color(ChatColor.AQUA);

    lines.add(header); // Header Line (server name) - START
    lines.add(Components.blank());
    lines.add(type.getScreenComponent(formatPunishmentReason(reason))); // The reason
    lines.add(Components.blank());

    // If punishment expires, inform user when
    if (expires != null) {
      Component timeLeft =
          PeriodFormats.briefNaturalApproximate(
              org.joda.time.Duration.standardSeconds(expires.getSeconds()));
      lines.add(
          new PersonalizedTranslatable("moderation.screen.expires", timeLeft)
              .getPersonalizedText()
              .color(ChatColor.GRAY));
      lines.add(Components.blank());
    }

    // Staff sign-off
    lines.add(
        new PersonalizedTranslatable("moderation.screen.signoff", punisher)
            .getPersonalizedText()
            .color(ChatColor.GRAY)); // The sign-off of who performed the punishment
    lines.add(Components.blank());

    // Link to rules for review by player
    if (Config.Moderation.isRuleLinkVisible()) {
      lines.add(
          new PersonalizedTranslatable("moderation.screen.rulesLink", rules)
              .getPersonalizedText()
              .color(ChatColor.GRAY)); // A link to the rules
    }
    lines.add(Components.blank());

    lines.add(footer); // Footer line - END

    return Components.join(new PersonalizedText("\n" + ChatColor.RESET), lines).toLegacyText();
  }

  /*
   * Sends a formatted title and plays a sound warning a user of their actions
   */
  private void sendWarning(MatchPlayer target, String reason) {
    showWarningTitle(target, reason);
    target.playSound(WARN_SOUND);
  }

  private void showWarningTitle(MatchPlayer target, String reason) {
    Component titleWord =
        new PersonalizedTranslatable("moderation.warning")
            .getPersonalizedText()
            .color(ChatColor.DARK_RED);
    Component title = new PersonalizedText(WARN_SYMBOL, titleWord, WARN_SYMBOL);
    Component subtitle = formatPunishmentReason(reason).color(ChatColor.GOLD);

    target.showTitle(title, subtitle, 5, 200, 10);
  }

  private void broadcastPunishment(PlayerPunishmentEvent event) {
    broadcastPunishment(
        event.getType(),
        event.getPlayer().getMatch(),
        event.getSender(),
        event.getPlayer(),
        event.getReason(),
        event.isSilent());
  }

  /*
   * Broadcasts a punishment
   */
  private void broadcastPunishment(
      PunishmentType type,
      Match match,
      CommandSender sender,
      MatchPlayer target,
      String reason,
      boolean silent) {
    Component prefix =
        new PersonalizedTranslatable("moderation.punishment.prefix", type.getPunishmentPrefix())
            .getPersonalizedText()
            .color(ChatColor.GOLD);
    Component targetName = target.getStyledName(NameStyle.FANCY);
    Component reasonMsg = ModerationCommands.formatPunishmentReason(reason);
    Component formattedMsg =
        new PersonalizedText(
            prefix,
            Components.space(),
            ModerationCommands.formatPunisherName(sender, match),
            BROADCAST_DIV,
            targetName,
            BROADCAST_DIV,
            reasonMsg);

    if (!silent) {
      match.sendMessage(formattedMsg);
    } else {
      // if silent flag present, only notify sender
      sender.sendMessage(formattedMsg);
    }
  }

  /*
   * Bukkit method of banning players
   * NOTE: Will use this if not handled by other plugins
   */
  private void banPlayer(PlayerPunishmentEvent event, @Nullable Instant expires) {
    Bukkit.getBanList(BanList.Type.NAME)
        .addBan(
            event.getPlayer().getBukkit().getName(),
            event.getReason(),
            expires != null ? Date.from(expires) : null,
            event.getSender().getName());
  }
}
