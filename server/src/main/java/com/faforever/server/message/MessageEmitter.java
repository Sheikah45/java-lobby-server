package com.faforever.server.message;

import com.faforever.server.message.internal.MessageRequest;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.jbosslog.JBossLog;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

@JBossLog
@ApplicationScoped
public class MessageEmitter {

    private final Emitter<MessageRequest> lobbyMessageEmitter;

    public MessageEmitter(@Channel("client-outbound") Emitter<MessageRequest> lobbyMessageEmitter) {
        this.lobbyMessageEmitter = lobbyMessageEmitter;
    }

    public void send(MessageRequest messageRequest) {
        lobbyMessageEmitter.send(messageRequest);
    }
}
