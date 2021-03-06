package io.luna.game.model.mobile;

import com.google.common.base.MoreObjects;
import io.luna.LunaContext;
import io.luna.game.event.impl.LoginEvent;
import io.luna.game.event.impl.LogoutEvent;
import io.luna.game.model.Direction;
import io.luna.game.model.EntityConstants;
import io.luna.game.model.EntityType;
import io.luna.game.model.Position;
import io.luna.game.model.mobile.update.UpdateFlagHolder.UpdateFlag;
import io.luna.net.codec.ByteMessage;
import io.luna.net.msg.OutboundGameMessage;
import io.luna.net.msg.out.SendAssignmentMessage;
import io.luna.net.msg.out.SendGameInfoMessage;
import io.luna.net.msg.out.SendLogoutMessage;
import io.luna.net.msg.out.SendRegionChangeMessage;
import io.luna.net.msg.out.SendSkillUpdateMessage;
import io.luna.net.msg.out.SendTabInterfaceMessage;
import io.luna.net.session.GameSession;
import io.luna.net.session.Session;
import io.netty.channel.Channel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

/**
 * A {@link MobileEntity} implementation that is controlled by a real person.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class Player extends MobileEntity {

    /**
     * The logger that will print important information.
     */
    private static final Logger LOGGER = LogManager.getLogger(Player.class);

    /**
     * The {@link Set} of local {@code Player}s.
     */
    private final Set<Player> localPlayers = new LinkedHashSet<>();

    /**
     * The {@link Set} of local {@code Npc}s.
     */
    private final Set<Npc> localNpcs = new LinkedHashSet<>();

    /**
     * The {@link PlayerAppearance} container assigned to this player.
     */
    private final PlayerAppearance appearance = new PlayerAppearance();

    /**
     * The credentials of this {@code Player}.
     */
    private final PlayerCredentials credentials;

    /**
     * The current cached block for this update cycle.
     */
    private ByteMessage cachedBlock;

    /**
     * The authority level of this {@code Player}.
     */
    private PlayerRights rights = PlayerRights.PLAYER;

    /**
     * The {@link Session} assigned to this {@code Player}.
     */
    private GameSession session;

    /**
     * The last known region that this {@code Player} was in.
     */
    private Position lastRegion;

    /**
     * If the region has changed during this cycle.
     */
    private boolean regionChanged;

    /**
     * The walking direction of this {@code Player}.
     */
    private Direction runningDirection = Direction.NONE;

    /**
     * The {@link Chat} message to send during this update block.
     */
    private Chat chat;

    /**
     * The {@link ForceMovement} that dictates where this {@code Player} will be forced to move.
     */
    private ForceMovement forceMovement;

    /**
     * The identifier of the {@link Npc} to transform into.
     */
    private int transformId = -1;

    /**
     * Creates a new {@link Player}.
     *
     * @param context The context to be managed in.
     */
    public Player(LunaContext context, PlayerCredentials credentials) {
        super(context);
        this.credentials = credentials;
        setPosition(EntityConstants.STARTING_POSITION);
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public EntityType type() {
        return EntityType.PLAYER;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getUsernameHash());
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("username", getUsername()).add("rights", rights).toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof Player) {
            Player other = (Player) obj;
            return other.getUsernameHash() == getUsernameHash();
        }
        return false;
    }

    @Override
    public void onActive() {
        updateFlags.flag(UpdateFlag.APPEARANCE);

        queue(new SendAssignmentMessage(true));

        int[] interfaces = { 2423, 3917, 638, 3213, 1644, 5608, 1151, -1, 5065, 5715, 2449, 904, 147, 962 };
        for (int index = 0; index < interfaces.length; index++) {
            queue(new SendTabInterfaceMessage(index, interfaces[index]));
        }

        int size = SkillSet.size();
        for (int index = 0; index < size; index++) {
            queue(new SendSkillUpdateMessage(index));
        }

        if (session.getHostAddress().equals("127.0.0.1")) {
            rights = PlayerRights.DEVELOPER;
        }

        queue(new SendGameInfoMessage("Welcome to Luna!"));

        plugins.post(new LoginEvent(), this);

        LOGGER.info("{} has logged in.", this);
    }

    @Override
    public void onInactive() {
        plugins.post(new LogoutEvent());

        LOGGER.info("{} has logged out.", this);

        PlayerSerializer serializer = new PlayerSerializer(this);
        serializer.asyncSave(service);
    }

    @Override
    public void reset() {
        chat = null;
        regionChanged = false;
    }

    /**
     * Gracefully logs this {@code Player} instance out of the server.
     */
    public void logout() {
        Channel channel = session.getChannel();
        if (channel.isActive()) {
            queue(new SendLogoutMessage());
        }
    }

    /**
     * Send {@code chat} message for this cycle.
     *
     * @param chat The {@link Chat} message to send during this update block.
     */
    public void chat(Chat chat) {
        this.chat = requireNonNull(chat);
        updateFlags.flag(UpdateFlag.CHAT);
    }

    /**
     * Send {@code forceMovement} message for this cycle.
     *
     * @param forceMovement The {@link ForceMovement} that dictates where this {@code Player} will be forced to move.
     */
    public void forceMovement(ForceMovement forceMovement) {
        this.forceMovement = requireNonNull(forceMovement);
        updateFlags.flag(UpdateFlag.FORCE_MOVEMENT);
    }

    /**
     * Transforms this {@code Player} into the {@link Npc} represented by {@code npcId}. An identifier of {@code -1} disables
     * the transformation.
     *
     * @param npcId The {@code Npc} to transform this {@code Player} into.
     */
    public void transform(int npcId) {
        transformId = npcId;
        updateFlags.flag(UpdateFlag.APPEARANCE);
    }

    /**
     * A shortcut function to {@link GameSession#queue(OutboundGameMessage)}.
     */
    public void queue(OutboundGameMessage msg) {
        session.queue(msg);
    }

    /**
     * @return {@code true} if the position and last known region of the {@code Player} means a {@link
     * SendRegionChangeMessage} message needs to be queued, {@code false} otherwise.
     */
    public boolean needsRegionUpdate() {
        int deltaX = position.getLocalX(lastRegion);
        int deltaY = position.getLocalY(lastRegion);

        return deltaX < 16 || deltaX >= 88 || deltaY < 16 || deltaY > 88;
    }

    /**
     * @return The authority level of this {@code Player}.
     */
    public PlayerRights getRights() {
        return rights;
    }

    /**
     * Sets the value for {@link #rights}.
     */
    public void setRights(PlayerRights rights) {
        this.rights = rights;
    }

    /**
     * @return The username of this {@code Player}.
     */
    public String getUsername() {
        return credentials.getUsername();
    }

    /**
     * @return The password of this {@code Player}.
     */
    public String getPassword() {
        return credentials.getPassword();
    }

    /**
     * @return The username hash of this {@code Player}.
     */
    public long getUsernameHash() {
        return credentials.getUsernameHash();
    }

    /**
     * @return The {@link Session} assigned to this {@code Player}.
     */
    public GameSession getSession() {
        return session;
    }

    /**
     * Sets the value for {@link #session}.
     */
    public void setSession(GameSession session) {
        checkState(this.session == null, "session already set!");
        this.session = session;
    }

    /**
     * @return The {@link Set} of local {@code Player}s.
     */
    public Set<Player> getLocalPlayers() {
        return localPlayers;
    }

    /**
     * @return The {@link Set} of local {@code Npc}s.
     */
    public Set<Npc> getLocalNpcs() {
        return localNpcs;
    }

    /**
     * @return The current cached block for this update cycle.
     */
    public ByteMessage getCachedBlock() {
        return cachedBlock;
    }

    /**
     * Sets the value for {@link #cachedBlock}.
     */
    public void setCachedBlock(ByteMessage cachedBlock) {
        ByteMessage currentBlock = this.cachedBlock;

        // Release reference to currently cached block, if we have one.
        if (currentBlock != null) {
            currentBlock.release();
        }

        // If we need to set a new cached block, retain a reference to it. Otherwise it means that the current block
        // reference was just being cleared.
        if (cachedBlock != null) {
            cachedBlock.retain();
        }
        this.cachedBlock = cachedBlock;
    }

    /**
     * @return The last known region that this {@code Player} was in.
     */
    public Position getLastRegion() {
        return lastRegion;
    }

    /**
     * Sets the value for {@link #lastRegion}.
     */
    public void setLastRegion(Position lastRegion) {
        this.lastRegion = lastRegion;
    }

    /**
     * @return {@code true} if the region has changed during this cycle, {@code false} otherwise.
     */
    public boolean isRegionChanged() {
        return regionChanged;
    }

    /**
     * Sets the value for {@link #regionChanged}.
     */
    public void setRegionChanged(boolean regionChanged) {
        this.regionChanged = regionChanged;
    }

    /**
     * @return The walking direction of this {@code Player}.
     */
    public Direction getRunningDirection() {
        return runningDirection;
    }

    /**
     * Sets the value for {@link #runningDirection}.
     */
    public void setRunningDirection(Direction runningDirection) {
        this.runningDirection = runningDirection;
    }

    /**
     * @return The {@link Chat} message to send during this update block.
     */
    public Chat getChat() {
        return chat;
    }

    /**
     * @return The {@link ForceMovement} that dictates where this {@code Player} will be forced to move.
     */
    public ForceMovement getForceMovement() {
        return forceMovement;
    }

    /**
     * @return The {@link PlayerAppearance} container assigned to this player.
     */
    public PlayerAppearance getAppearance() {
        return appearance;
    }

    /**
     * @return The identifier of the {@link Npc} to transform into.
     */
    public int getTransformId() {
        return transformId;
    }
}
