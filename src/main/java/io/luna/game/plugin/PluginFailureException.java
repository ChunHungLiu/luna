package io.luna.game.plugin;

import io.luna.game.event.EventListenerPipeline;

/**
 * A {@link RuntimeException} implementation thrown when an unhandled {@link Exception} is thrown up to the {@link
 * EventListenerPipeline} from within a plugin.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class PluginFailureException extends RuntimeException {

    /**
     * Creates a new {@link PluginFailureException}.
     *
     * @param reason The reason for the plugin failure.
     */
    public PluginFailureException(Object reason) {
        super(String.valueOf(reason));
    }

    /**
     * Creates a new {@link PluginFailureException} with {@code cause} as the cause.
     *
     * @param cause The {@link Exception} to wrap within this {@code PluginFailureException}.
     */
    public PluginFailureException(Exception cause) {
        super(cause.getMessage(), cause);
    }
}
