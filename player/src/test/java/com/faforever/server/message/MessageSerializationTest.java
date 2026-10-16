package com.faforever.server.message;

import com.faforever.server.message.client.AdminMessage;
import com.faforever.server.message.client.LobbyMessage;
import com.faforever.server.message.service.OutboundLobbyMessage;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

@QuarkusTest
public class MessageSerializationTest {

    @Inject
    JsonMapper jsonMapper;

    @Test
    void testOutboundSerialization() throws Exception {
        OutboundLobbyMessage<? extends LobbyMessage.Server> message = OutboundLobbyMessage.forAll(new AdminMessage.NoticeInfo("test", AdminMessage.Style.INFO));

        JSONAssert.assertEquals("""
                {
                    "target": {"@type": "all"},
                    "message": {
                        "command": "notice",
                        "message": "test",
                        "style": "info"
                    }
                }
                """, jsonMapper.writeValueAsString(message), JSONCompareMode.NON_EXTENSIBLE);
    }
}
