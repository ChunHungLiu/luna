package io.luna.net.msg;

import io.luna.game.model.mobile.Player;
import org.apache.logging.log4j.core.config.plugins.util.PluginManager;
import plugin.PluginEvent;

/**
 * An inbound message handler that decodes all incoming {@link GameMessage}s.
 *
 * @author lare96 <http://github.org/lare96>
 */
public abstract class InboundGameMessage {

    /**
     * Read the {@code msg} and return the {@code Object} event that will be forwarded to the {@link PluginManager}, if any.
     * This is only used for the decoding, validation, basic logic stages of an incoming message. All event type logic should
     * be handled within plugins.
     *
     * @param player The player.
     * @param msg The message to read.
     * @return The {@code Object} that will be forwarded to a plugin, {@code null} if no {@code Object} should be forwarded.
     * @throws Exception If any exceptions are thrown. Will later be caught by the session logger.
     */
    public abstract PluginEvent readMessage(Player player, GameMessage msg) throws Exception;
}
