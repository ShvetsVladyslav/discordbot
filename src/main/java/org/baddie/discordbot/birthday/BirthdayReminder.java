package org.baddie.discordbot.birthday;

import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.baddie.discordbot.SheetsWriter;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.MonthDay;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Log4j2
@Component
@ConditionalOnProperty(prefix = "service.birthdays", name = "enabled", havingValue = "true")
public class BirthdayReminder {

    private static final Pattern DAY_MONTH_YEAR = Pattern.compile("^(\\d{1,2})[./-](\\d{1,2})(?:[./-](\\d{2,4}))?$");
    private static final DateTimeFormatter OUTPUT_DATE = DateTimeFormatter.ofPattern("dd.MM", Locale.ROOT);

    private final JDA jda;
    private final SheetsWriter sheetsWriter;
    private final BirthdayProperties properties;

    public BirthdayReminder(JDA jda, SheetsWriter sheetsWriter, BirthdayProperties properties) {
        this.jda = jda;
        this.sheetsWriter = sheetsWriter;
        this.properties = properties;
    }

    @Scheduled(cron = "${service.birthdays.cron:0 0 3 * * *}", zone = "${service.birthdays.zone:Europe/Kiev}")
    public void sendBirthdayReminder() {
        if (!hasText(properties.getDiscordChannelId())) {
            log.warn("Birthday reminders are enabled, but Discord channel id is missing.");
            return;
        }
        if (!hasText(properties.getTableId())) {
            log.warn("Birthday reminders are enabled, but Google spreadsheet table id is missing.");
            return;
        }

        try {
            List<BirthdayEntry> upcoming = loadUpcomingBirthdays(LocalDate.now());
            if (upcoming.isEmpty() && !properties.isNotifyWhenEmpty()) {
                log.info("No upcoming birthdays found.");
                return;
            }

            sendMessage(buildMessage(upcoming));
        } catch (Exception e) {
            log.warn("Could not send birthday reminder.", e);
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void sendBirthdayReminderOnStartup() {
        sendBirthdayReminder();
    }

    List<BirthdayEntry> loadUpcomingBirthdays(LocalDate today) throws Exception {
        List<List<Object>> rows = sheetsWriter.readRows(properties.getTableId(), properties.getSheetName(), properties.getRange());
        List<BirthdayEntry> birthdays = new ArrayList<>();

        for (int i = 0; i < rows.size(); i++) {
            if (i == 0 && properties.isSkipHeader()) {
                continue;
            }

            List<Object> row = rows.get(i);
            if (row.size() < 2) {
                continue;
            }

            String name = String.valueOf(row.get(0)).trim();
            String dateRaw = String.valueOf(row.get(1)).trim();
            if (!hasText(name) || !hasText(dateRaw)) {
                continue;
            }

            Optional<ParsedBirthday> parsed = parseBirthday(dateRaw);
            if (parsed.isEmpty()) {
                log.warn("Could not parse birthday date '{}' for '{}'.", dateRaw, name);
                continue;
            }

            LocalDate nextBirthday = nextBirthday(today, parsed.get().monthDay());
            long daysUntil = ChronoUnit.DAYS.between(today, nextBirthday);
            if (daysUntil <= Math.max(0, properties.getLookAheadDays())) {
                birthdays.add(new BirthdayEntry(name, nextBirthday, daysUntil));
            }
        }

        birthdays.sort(Comparator
                .comparingLong(BirthdayEntry::daysUntil)
                .thenComparing(BirthdayEntry::name, String.CASE_INSENSITIVE_ORDER));
        return birthdays;
    }

    String buildMessage(List<BirthdayEntry> birthdays) {
        if (birthdays.isEmpty()) {
            return "На ближайшие " + properties.getLookAheadDays() + " дней дней рождений не найдено.";
        }

        StringBuilder message = new StringBuilder("Ближайшие дни рождения:\n");
        for (BirthdayEntry birthday : birthdays) {
            message.append("- ")
                    .append(formatWhen(birthday))
                    .append(": ")
                    .append(birthday.name());

            message.append('\n');
        }

        return message.toString().trim();
    }

    private void sendMessage(String message) {
        TextChannel channel = jda.getTextChannelById(properties.getDiscordChannelId());
        if (channel == null) {
            log.warn("Discord channel {} was not found for birthday reminders.", properties.getDiscordChannelId());
            return;
        }

        Member selfMember = channel.getGuild().getSelfMember();
        if (!selfMember.hasPermission(channel, Permission.MESSAGE_SEND)) {
            log.warn("Bot cannot send birthday reminders to channel {}. Missing permission: Send Messages.", properties.getDiscordChannelId());
            return;
        }

        channel.sendMessage(message).queue(
                success -> log.info("Birthday reminder sent to channel {}", properties.getDiscordChannelId()),
                error -> log.warn("Could not send birthday reminder message.", error)
        );
    }

    private String formatWhen(BirthdayEntry birthday) {
        if (birthday.daysUntil() == 0) {
            return "сегодня, " + birthday.date().format(OUTPUT_DATE);
        }
        if (birthday.daysUntil() == 1) {
            return "завтра, " + birthday.date().format(OUTPUT_DATE);
        }
        return birthday.date().format(OUTPUT_DATE) + " (через " + birthday.daysUntil() + " " + dayWord(birthday.daysUntil()) + ")";
    }

    private String dayWord(long days) {
        long mod10 = days % 10;
        long mod100 = days % 100;
        if (mod10 == 1 && mod100 != 11) {
            return "день";
        }
        if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) {
            return "дня";
        }
        return "дней";
    }

    private Optional<ParsedBirthday> parseBirthday(String raw) {
        String value = raw.trim();

        try {
            LocalDate date = LocalDate.parse(value);
            return Optional.of(new ParsedBirthday(MonthDay.from(date), date.getYear()));
        } catch (DateTimeParseException ignored) {
            // Try common table formats below.
        }

        Matcher matcher = DAY_MONTH_YEAR.matcher(value);
        if (!matcher.matches()) {
            return Optional.empty();
        }

        int day = Integer.parseInt(matcher.group(1));
        int month = Integer.parseInt(matcher.group(2));
        Integer year = parseYear(matcher.group(3));

        try {
            return Optional.of(new ParsedBirthday(MonthDay.of(month, day), year));
        } catch (DateTimeParseException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private Integer parseYear(String value) {
        if (!hasText(value)) {
            return null;
        }

        int year = Integer.parseInt(value);
        if (year < 100) {
            return year >= 50 ? 1900 + year : 2000 + year;
        }
        return year;
    }

    private LocalDate nextBirthday(LocalDate today, MonthDay birthday) {
        LocalDate next = birthday.atYear(today.getYear());
        if (next.isBefore(today)) {
            next = birthday.atYear(today.getYear() + 1);
        }
        return next;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    record BirthdayEntry(String name, LocalDate date, long daysUntil) {
    }

    private record ParsedBirthday(MonthDay monthDay, Integer year) {
    }
}
