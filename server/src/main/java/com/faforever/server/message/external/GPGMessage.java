package com.faforever.server.message.external;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.List;

public sealed interface GPGMessage extends LobbyMessage {

    sealed interface Server extends GPGMessage, LobbyMessage.Server {}

    sealed interface Client extends GPGMessage, Authenticated {

        sealed interface HostOnly extends GPGMessage.Client {}

        @JsonCreator
        static GPGMessage.Client of(String command, String target, List<Object> args) {
            if (!"game".equals(target)) {
                throw new IllegalArgumentException("GPG Game message with wrong target");
            }
            return switch (command) {
                case "AIOption" -> new AIOption(args);
                case "Bottleneck" -> new Bottleneck(args);
                case "BottleneckCleared" -> new BottleneckCleared(args);
                case "Chat" -> new Chat(args);
                case "ClearSlot" -> new ClearSlot(args);
                case "Desync" -> new Desync(args);
                case "Disconnected" -> new Disconnected(args);
                case "EnforceRating" -> new EnforceRating(args);
                case "GameEnded" -> new GameEnded(args);
                case "GameFull" -> new GameFull(args);
                case "GameMods" -> new GameMods(args);
                case "GameOption" -> new GameOption(args);
                case "GameResult" -> new GameResult(args);
                case "GameState" -> new GameState(args);
                case "IceMsg" -> new IceMsg(args);
                case "JsonStats" -> new JsonStats(args);
                case "LaunchStatus" -> new LaunchStatus(args);
                case "OperationComplete" -> new OperationComplete(args);
                case "PlayerOption" -> new PlayerOption(args);
                case "Rehost" -> new Rehost(args);
                case "TeamkillHappened" -> new TeamkillHappened(args);
                case "TeamkillReport" -> new TeamkillReport(args);

                default -> new UnknownMessage(command, args);
            };
        }

    }

    default String target() {
        return "game";
    }
    
    record AIOption(
            List<Object> args
    ) implements Client.HostOnly {

        public String aiName() {
            return (String) args.getFirst();
        }

        public String optionKey() {
            return (String) args.get(1);
        }

        public Object optionValue() {
            return args.get(2);
        }
    }

    record Bottleneck(
            List<Object> args
    ) implements GPGMessage.Client {
    }

    record BottleneckCleared(
            List<Object> args
    ) implements GPGMessage.Client {
    }

    record Chat(
            List<Object> args
    ) implements GPGMessage.Client {
    }

    record ClearSlot(
            List<Object> args
    ) implements Client.HostOnly {
    }

    record Desync(
            List<Object> args
    ) implements GPGMessage.Client {
    }

    record Disconnected(
            List<Object> args
    ) implements GPGMessage.Client {
    }

    record EnforceRating(
            List<Object> args
    ) implements GPGMessage.Client.HostOnly {
    }

    record GameEnded(
            List<Object> args
    ) implements GPGMessage.Client {
    }

    record GameFull(
            List<Object> args
    ) implements GPGMessage.Client {
    }

    record GameMods(
            List<Object> args
    ) implements GPGMessage.Client.HostOnly {
    }

    record GameOption(
            List<Object> args
    ) implements GPGMessage.Client.HostOnly {
    }

    record GameResult(
            List<Object> args
    ) implements GPGMessage.Client {
    }

    record GameState(
            List<Object> args
    ) implements GPGMessage.Client {

        public String state() {
            return (String) args.getFirst();
        }
    }

    record IceMsg(
            List<Object> args
    ) implements GPGMessage.Client {
    }

    record JsonStats(
            List<Object> args
    ) implements GPGMessage.Client {
    }

    record LaunchStatus(
            List<Object> args
    ) implements GPGMessage.Client {
    }

    record OperationComplete(
            List<Object> args
    ) implements GPGMessage.Client {
    }

    record PlayerOption(
            List<Object> args
    ) implements GPGMessage.Client.HostOnly {
    }

    record Rehost(
            List<Object> args
    ) implements GPGMessage.Client {
    }

    record TeamkillHappened(
            List<Object> args
    ) implements GPGMessage.Client {
    }

    record TeamkillReport(
            List<Object> args
    ) implements GPGMessage.Client {
    }

    record UnknownMessage(
            String command,
            List<Object> args
    ) implements GPGMessage.Client {}
    
    record HostGame(
            List<Object> args
    ) implements GPGMessage.Server {}

}
