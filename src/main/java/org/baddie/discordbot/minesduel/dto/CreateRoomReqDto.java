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
public class CreateRoomReqDto implements Serializable {

    private static final long serialVersionUID = 1L;

    private String creatorName;
    private String opponentName;
}
