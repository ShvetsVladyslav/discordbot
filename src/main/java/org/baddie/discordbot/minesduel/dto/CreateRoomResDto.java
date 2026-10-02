package org.baddie.discordbot.minesduel.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreateRoomResDto implements Serializable {

    private static final long serialVersionUID = 1L;

    private String code;
    private Object expiresAt;
    private Long ttlSeconds;
}
