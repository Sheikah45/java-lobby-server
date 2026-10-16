package com.faforever.server.messagebroker;

import com.faforever.server.message.service.PlayerConnectionDetails;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.Collection;

@ApplicationScoped
@RegisterRestClient(configKey = "message-broker")
public interface MessageBrokerAPIClient {

    @Path("/internal/api/sessions")
    @GET
    Collection<PlayerConnectionDetails> getActiveSessions();

}
