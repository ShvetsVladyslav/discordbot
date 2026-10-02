package org.baddie.discordbot.minesduel;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.baddie.discordbot.minesduel.dto.CreateRoomReqDto;
import org.baddie.discordbot.minesduel.dto.CreateRoomResDto;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Log4j2
@Service
public class MinesDuelApiService {

    private final RestTemplate restTemplate;

    private static final String CREATE_ROOM_URL = "https://mine-duel-room.burni.chatgpt.site/api/rooms";

    public MinesDuelApiService(RestTemplateBuilder restTemplateBuilder) {

        this.restTemplate = restTemplateBuilder.build();
    }

    public CreateRoomResDto createRoom(CreateRoomReqDto req) {

        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");

        HttpEntity<CreateRoomReqDto> entity = new HttpEntity<>(req, headers);
        return restTemplate.postForObject(CREATE_ROOM_URL, entity, CreateRoomResDto.class);
    }

}
