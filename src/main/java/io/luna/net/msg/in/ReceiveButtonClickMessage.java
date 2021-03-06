package io.luna.net.msg.in;

import io.luna.game.event.Event;
import io.luna.game.event.impl.ButtonClickEvent;
import io.luna.game.model.mobile.Player;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.InboundGameMessage;

/**
 * An {@link InboundGameMessage} implementation that decodes data sent when a {@link Player} clicks widgets on an interface.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class ReceiveButtonClickMessage extends InboundGameMessage {

    @Override
    public Event readMessage(Player player, GameMessage msg) throws Exception {
        int widget = msg.getPayload().getShort(false);

        // TODO: Ensure that 'widget' is a valid widget on the interface currently open (if one is open)
        return new ButtonClickEvent(widget);
    }
}
