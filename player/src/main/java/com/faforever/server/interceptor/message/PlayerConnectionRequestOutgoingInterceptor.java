package com.faforever.server.interceptor.message;

import com.faforever.server.interceptor.message.util.CompundRabbitMQOutgoingInterceptor;
import com.faforever.server.interceptor.message.util.PersistentRabbitMQOutgoingInterceptor;
import com.faforever.server.interceptor.message.util.ReplyToRabbitMQOutgoingInterceptor;
import io.smallrye.common.annotation.Identifier;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;

@Singleton
@Identifier("player-connection-request")
public class PlayerConnectionRequestOutgoingInterceptor extends CompundRabbitMQOutgoingInterceptor {

    public PlayerConnectionRequestOutgoingInterceptor(@ConfigProperty(name = "mp.messaging.incoming.player-connection-response.queue.name") String queueName) {
        super(List.of(new PersistentRabbitMQOutgoingInterceptor(), new ReplyToRabbitMQOutgoingInterceptor(queueName)));
    }

}
