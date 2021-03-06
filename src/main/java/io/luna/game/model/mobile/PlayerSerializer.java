package io.luna.game.model.mobile;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.moandjiezana.toml.Toml;
import com.moandjiezana.toml.TomlWriter;
import io.luna.game.GameService;
import io.luna.game.model.Position;
import io.luna.game.model.mobile.attr.AttributeKey;
import io.luna.game.model.mobile.attr.AttributeValue;
import io.luna.net.codec.login.LoginResponse;
import io.luna.util.GsonUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;

/**
 * Functions that allow for synchronous and asynchronous serialization and deserialization of a {@link Player}.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class PlayerSerializer {

    /**
     * The logger that will print important information.
     */
    private static final Logger LOGGER = LogManager.getLogger(PlayerSerializer.class);

    /**
     * The {@link Path} to all of the serialized {@link Player} data.
     */
    private static final Path FILE_DIR = Paths.get("./data/saved_players");

    /**
     * The {@link Player} being serialized or deserialized.
     */
    private final Player player;

    /**
     * The {@link Path} to the character file.
     */
    private final Path path;

    /**
     * Creates a new {@link PlayerSerializer}.
     *
     * @param player The {@link Player} being serialized or deserialized.
     */
    public PlayerSerializer(Player player) {
        this.player = player;
        path = FILE_DIR.resolve(player.getUsername() + ".toml");
    }

    static {
        if (Files.notExists(FILE_DIR)) {
            try {
                Files.createDirectory(FILE_DIR);
            } catch (Exception e) {
                LOGGER.catching(e);
            }
        }
    }

    /**
     * Attempts to serialize all persistent data for the {@link Player}.
     */
    public void save() {
        TomlWriter writer = new TomlWriter();
        Map<String, Object> data = new LinkedHashMap<>();

        data.put("password", player.getPassword());
        data.put("position", player.getPosition());
        data.put("rights", player.getRights().name());
        data.put("running", player.getWalkingQueue().isRunning());
        data.put("appearance", player.getAppearance().toArray());

        Map<String, Object> attributes = new HashMap<>();
        for (Entry<String, AttributeValue<?>> it : player.attributes) {
            AttributeKey<?> key = AttributeKey.ALIASES.get(it.getKey());
            AttributeValue<?> value = it.getValue();

            if (key.isPersistent()) {
                Map<String, Object> attributeEntry = new LinkedHashMap<>();
                attributeEntry.put("type", key.getTypeName());
                attributeEntry.put("value", value.get());

                attributes.put(key.getName(), attributeEntry);
            }
        }
        data.put("attributes", attributes);

        try {
            writer.write(data, path.toFile());
        } catch (IOException e) {
            LOGGER.catching(e);
        }
    }

    /**
     * Attempts to asynchronously serialize all persistent data for the {@link Player}. Returns a {@link ListenableFuture}
     * detailing the progress and result of the asynchronous task.
     *
     * @param service The {@link GameService} to use for asynchronous execution.
     * @return The {@link ListenableFuture} detailing progress and the result.
     */
    public ListenableFuture<Void> asyncSave(GameService service) {
        return service.submit((Callable<Void>) () -> {
            save();
            return null;
        });
    }

    /**
     * Attempts to deserialize all persistent data for the {@link Player}.
     *
     * @param expectedPassword The expected password to be compared against the deserialized password.
     * @return The {@link LoginResponse} determined by the deserialization.
     */
    public LoginResponse load(String expectedPassword) {
        if (!Files.exists(path)) {
            return LoginResponse.NORMAL;
        }
        try {
            JsonObject reader = new Toml().read(path.toFile()).to(JsonObject.class);

            String password = reader.get("password").getAsString();
            if (!expectedPassword.equals(password)) {
                return LoginResponse.INVALID_CREDENTIALS;
            }

            Position position = GsonUtils.getAsType(reader.get("position"), Position.class);
            player.setPosition(position);

            PlayerRights rights = PlayerRights.valueOf(reader.get("rights").getAsString());
            player.setRights(rights);

            boolean running = reader.get("running").getAsBoolean();
            player.getWalkingQueue().setRunning(running);

            int[] appearance = GsonUtils.getAsType(reader.get("appearance"), int[].class);
            player.getAppearance().setValues(appearance);

            JsonObject attr = reader.get("attributes").getAsJsonObject();
            for (Entry<String, JsonElement> it : attr.entrySet()) {
                JsonObject obj = it.getValue().getAsJsonObject();

                Class<?> type = Class.forName(obj.get("type").getAsString());
                Object data = GsonUtils.getAsType(obj.get("value"), type);

                player.attributes.get(it.getKey()).set(data);
            }
        } catch (Exception e) {
            LOGGER.catching(e);
            return LoginResponse.COULD_NOT_COMPLETE_LOGIN;
        }
        return LoginResponse.NORMAL;
    }

    /**
     * Attempts to asynchronously deserialize all persistent data for the {@link Player}. Returns a {@link ListenableFuture}
     * detailing the progress and result of the asynchronous task.
     *
     * @param expectedPassword The expected password to be compared against the deserialized password.
     * @param service The {@link GameService} to use for asynchronous execution.
     * @return The {@link ListenableFuture} detailing progress and the result.
     */
    public ListenableFuture<LoginResponse> asyncLoad(String expectedPassword, GameService service) {
        return service.submit(() -> load(expectedPassword));
    }
}
