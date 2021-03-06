package io.luna.game.event;

import com.google.common.collect.Iterators;
import com.google.common.collect.UnmodifiableIterator;
import io.luna.game.model.mobile.Player;
import io.luna.game.plugin.PluginFailureException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * A pipeline-like model that allows for an {@link Event} to be passed through it to be intercepted by each individual {@link
 * EventListener}. The traversal of the {@code Event} can be terminated at any time by invoking {@code terminate()}.
 * <p>
 * Please note that {@code EventListener}s can always be added to this pipeline, but they can <strong>never</strong> be
 * removed.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class EventListenerPipeline<E extends Event> implements Iterable<EventListener<E>> {

    /**
     * The logger that will print important information.
     */
    private static final Logger LOGGER = LogManager.getLogger(EventListenerPipeline.class);

    /**
     * A {@link List} of {@link EventListener} contained within this pipeline.
     */
    private final List<EventListener<E>> listeners = new ArrayList<>();

    /**
     * A flag that determines if a traversal has been terminated by a {@link EventListener}.
     */
    private boolean terminated;

    /**
     * Traverse the pipeline passing the {@code evt} instance to each {@link EventListener}. A full traversal over all {@code
     * EventListener}s is not always made.
     *
     * @param evt The event to pass to each {@code EventListener}.
     * @param player The {@link Player} to pass to each {@code EventListener}, possibly {@code null}.
     */
    public void traverse(E evt, Player player) {
        try {
            terminated = false;

            evt.setPipeline(this);
            for (EventListener<E> listener : listeners) {
                if (terminated) {
                    break;
                }
                try {
                    listener.getFunction().apply(evt, player);
                } catch (PluginFailureException failure) { // fail, recoverable
                    LOGGER.catching(failure);
                } catch (Exception other) { // unknown, unrecoverable
                    throw new PluginFailureException(other);
                }
            }
        } finally {
            evt.setPipeline(null);
        }
    }

    /**
     * Terminates an active traversal of this pipeline, if this pipeline is not currently being traversed then this method
     * does nothing.
     *
     * @return {@code true} if termination was successful, {@code false} if this pipeline traversal has already been
     * terminated.
     */
    public boolean terminate() {
        if (!terminated) {
            terminated = true;
            return true;
        }
        return false;
    }

    /**
     * Adds {@code listener} to the underlying pipeline. May throw a {@link ClassCastException} if the event type doesn't
     * match the other {@link EventListener}s in this pipeline.
     *
     * @param listener The {@code PluginFunction} to add.
     */
    @SuppressWarnings("unchecked")
    public void add(EventListener<?> listener) {
        listeners.add((EventListener<E>) listener);
    }

    @Override
    public UnmodifiableIterator<EventListener<E>> iterator() {
        return Iterators.unmodifiableIterator(listeners.iterator());
    }
}
