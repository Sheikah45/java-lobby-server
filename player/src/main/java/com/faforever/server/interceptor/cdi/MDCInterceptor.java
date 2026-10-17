package com.faforever.server.interceptor.cdi;

import com.faforever.server.message.client.LobbyMessage;
import com.faforever.server.message.service.InboundLobbyMessage;
import jakarta.annotation.Priority;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import org.slf4j.MDC;

import java.util.Objects;

@MDCPopulated
@Priority(0)
@Interceptor
public class MDCInterceptor {

    private static final String COMMAND = "command";
    private static final String REQUEST_ID = "requestId";
    private static final String PLAYER_ID = "playerId";
    private static final String SESSION_ID = "sessionId";

    @AroundInvoke
    Object logInvocation(InvocationContext context) throws Exception {
        boolean hasMessage = false;

        Object[] parameters = context.getParameters();
        if (parameters.length == 1) {
            if (Objects.requireNonNull(parameters[0]) instanceof InboundLobbyMessage<?>(
                    long sessionId, int playerId, LobbyMessage.Client message
            )) {
                hasMessage = true;
                MDC.pushByKey(REQUEST_ID, message.requestId());
                MDC.pushByKey(COMMAND, message.command());
                MDC.pushByKey(SESSION_ID, String.valueOf(sessionId));
                MDC.pushByKey(PLAYER_ID, String.valueOf(playerId));
            }
        }

        try {
            return context.proceed();
        } finally {
            if (hasMessage) {
                MDC.popByKey(SESSION_ID);
                MDC.popByKey(PLAYER_ID);
                MDC.popByKey(COMMAND);
                MDC.popByKey(REQUEST_ID);
            }
        }
    }

}
