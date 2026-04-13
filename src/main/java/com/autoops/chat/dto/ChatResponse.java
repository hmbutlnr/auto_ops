package com.autoops.chat.dto;

import com.autoops.host.model.Host;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {
    private String sessionId;
    private String message;
    private boolean success;
    private String error;
    private boolean needsHostSelection;
    private String toolCallId;
    private Host selectedHost;
}