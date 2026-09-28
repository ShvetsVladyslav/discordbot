package org.baddie.discordbot.birthday;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "service.birthdays")
public class BirthdayProperties {

    private boolean enabled;
    private String tableId;
    private String sheetName = "Ответы на форму (1)";
    private String range = "C:D";
    private boolean skipHeader = true;
    private String discordChannelId;
    private int lookAheadDays = 7;
    private boolean notifyWhenEmpty;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getTableId() {
        return tableId;
    }

    public void setTableId(String tableId) {
        this.tableId = tableId;
    }

    public String getSheetName() {
        return sheetName;
    }

    public void setSheetName(String sheetName) {
        this.sheetName = sheetName;
    }

    public String getRange() {
        return range;
    }

    public void setRange(String range) {
        this.range = range;
    }

    public boolean isSkipHeader() {
        return skipHeader;
    }

    public void setSkipHeader(boolean skipHeader) {
        this.skipHeader = skipHeader;
    }

    public String getDiscordChannelId() {
        return discordChannelId;
    }

    public void setDiscordChannelId(String discordChannelId) {
        this.discordChannelId = discordChannelId;
    }

    public int getLookAheadDays() {
        return lookAheadDays;
    }

    public void setLookAheadDays(int lookAheadDays) {
        this.lookAheadDays = lookAheadDays;
    }

    public boolean isNotifyWhenEmpty() {
        return notifyWhenEmpty;
    }

    public void setNotifyWhenEmpty(boolean notifyWhenEmpty) {
        this.notifyWhenEmpty = notifyWhenEmpty;
    }
}
