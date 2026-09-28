package org.baddie.discordbot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@RestController
@RequestMapping(value = "/api/test")
@RequiredArgsConstructor
public class TestController {

    @Autowired
    private SheetsWriter sheetsWriter;

    @GetMapping("/sheet-writer")
    public void testSheetsWriter() throws Exception {
        sheetsWriter.appendRow(List.of(
                OffsetDateTime.now().toString(),
                "test_discord_id",
                "test_discord_name",
                "mid",
                "jg",
                "emerald 2"
        ));

        log.info("DONE");
    }
}
