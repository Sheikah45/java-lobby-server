package com.faforever.server.social;

import com.faforever.server.message.SocialMessage;
import com.faforever.server.session.SessionController;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.jbosslog.JBossLog;
import org.jspecify.annotations.Nullable;

@JBossLog
@RequiredArgsConstructor
@ApplicationScoped
public class SocialService {

    private final SessionController sessionController;

    public void handleMessage(SocialMessage.Client message) {
        switch (message) {
            case SocialMessage.SocialAddRequest(@Nullable Integer friend, @Nullable Integer foe) -> {}
            case SocialMessage.SocialRemoveRequest(@Nullable Integer friend, @Nullable Integer foe) -> {}
            case SocialMessage.ListAvatarsRequest() -> {}
            case SocialMessage.SelectAvatarRequest(@Nullable String avatar) -> {}
        }
    }

}
