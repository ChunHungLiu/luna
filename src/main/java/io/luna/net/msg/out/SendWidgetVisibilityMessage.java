package io.luna.net.msg.out;

import io.luna.game.model.mobile.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.msg.OutboundGameMessage;

/**
 * An {@link OutboundGameMessage} implementation that sets a widget to be hidden until hovered over.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class SendWidgetVisibilityMessage extends OutboundGameMessage {

    /**
     * The identifier of the widget to make hidden or unhidden.
     */
    private final int id;

    /**
     * If the widget should be hidden or unhidden.
     */
    private final boolean hide;

    /**
     * Creates a new {@link SendWidgetVisibilityMessage}.
     *
     * @param id The identifier of the widget to make hidden or unhidden.
     * @param hide If the widget should be hidden or unhidden.
     */
    public SendWidgetVisibilityMessage(int id, boolean hide) {
        this.id = id;
        this.hide = hide;
    }

    @Override
    public ByteMessage writeMessage(Player player) {
        ByteMessage msg = ByteMessage.message(171);
        msg.put(hide ? 1 : 0);
        msg.putShort(id);
        return msg;
    }
}
