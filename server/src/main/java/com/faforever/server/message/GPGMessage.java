package com.faforever.server.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonTypeId;

import java.util.List;

public sealed interface GPGMessage extends LobbyMessage {

    sealed interface Server extends GPGMessage, LobbyMessage.Server {}

    sealed interface Client extends GPGMessage, LobbyMessage.Client {

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

    @JsonTypeId
    String command();
    
    record AIOption(
            List<Object> args
    ) implements GPGMessage.Client {
        @Override
        public String command() {
            return "AIOption";
        }

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
        @Override
        public String command() {
            return "Bottleneck";
        }
    }

    record BottleneckCleared(
            List<Object> args
    ) implements GPGMessage.Client {
        @Override
        public String command() {
            return "BottleneckCleared";
        }
    }

    record Chat(
            List<Object> args
    ) implements GPGMessage.Client {
        @Override
        public String command() {
            return "Chat";
        }
    }

    record ClearSlot(
            List<Object> args
    ) implements GPGMessage.Client {
        @Override
        public String command() {
            return "ClearSlot";
        }
    }

    record Desync(
            List<Object> args
    ) implements GPGMessage.Client {
        @Override
        public String command() {
            return "Desync";
        }
    }

    record Disconnected(
            List<Object> args
    ) implements GPGMessage.Client {
        @Override
        public String command() {
            return "Disconnected";
        }
    }

    record EnforceRating(
            List<Object> args
    ) implements GPGMessage.Client {
        @Override
        public String command() {
            return "EnforceRating";
        }
    }

    record GameEnded(
            List<Object> args
    ) implements GPGMessage.Client {
        @Override
        public String command() {
            return "GameEnded";
        }
    }

    record GameFull(
            List<Object> args
    ) implements GPGMessage.Client {
        @Override
        public String command() {
            return "GameFull";
        }
    }

    record GameMods(
            List<Object> args
    ) implements GPGMessage.Client {
        @Override
        public String command() {
            return "GameMods";
        }
    }

    record GameOption(
            List<Object> args
    ) implements GPGMessage.Client {
        @Override
        public String command() {
            return "GameOption";
        }
    }

    record GameResult(
            List<Object> args
    ) implements GPGMessage.Client {
        @Override
        public String command() {
            return "GameResult";
        }
    }

    record GameState(
            List<Object> args
    ) implements GPGMessage.Client {
        @Override
        public String command() {
            return "GameState";
        }

        public String state() {
            return (String) args.getFirst();
        }
    }

    record IceMsg(
            List<Object> args
    ) implements GPGMessage.Client {
        @Override
        public String command() {
            return "IceMsg";
        }
    }

    record JsonStats(
            List<Object> args
    ) implements GPGMessage.Client {
        @Override
        public String command() {
            return "JsonStats";
        }
    }

    record LaunchStatus(
            List<Object> args
    ) implements GPGMessage.Client {
        @Override
        public String command() {
            return "LaunchStatus";
        }
    }

    record OperationComplete(
            List<Object> args
    ) implements GPGMessage.Client {
        @Override
        public String command() {
            return "OperationComplete";
        }
    }

    record PlayerOption(
            List<Object> args
    ) implements GPGMessage.Client {
        @Override
        public String command() {
            return "PlayerOption";
        }
    }

    record Rehost(
            List<Object> args
    ) implements GPGMessage.Client {
        @Override
        public String command() {
            return "Rehost";
        }
    }

    record TeamkillHappened(
            List<Object> args
    ) implements GPGMessage.Client {
        @Override
        public String command() {
            return "TeamkillHappened";
        }
    }

    record TeamkillReport(
            List<Object> args
    ) implements GPGMessage.Client {
        @Override
        public String command() {
            return "TeamkillReport";
        }
    }

    record UnknownMessage(
            String command,
            List<Object> args
    ) implements GPGMessage.Client {}
    
    record HostGame(
            List<Object> args
    ) implements GPGMessage.Server {

        @Override
        public String command() {
            return "HostGame";
        }

    }

}
