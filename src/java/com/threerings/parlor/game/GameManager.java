//
// $Id: GameManager.java,v 1.49 2002/10/24 07:01:08 shaper Exp $

package com.threerings.parlor.game;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

import com.samskivert.util.StringUtil;

import com.threerings.presents.data.ClientObject;
import com.threerings.presents.dobj.AttributeChangeListener;
import com.threerings.presents.dobj.AttributeChangedEvent;
import com.threerings.presents.dobj.MessageEvent;

import com.threerings.crowd.chat.SpeakProvider;

import com.threerings.crowd.data.BodyObject;
import com.threerings.crowd.data.PlaceObject;

import com.threerings.crowd.server.PlaceManager;
import com.threerings.crowd.server.CrowdServer;
import com.threerings.crowd.server.CrowdServer;
import com.threerings.crowd.server.PlaceManagerDelegate;

import com.threerings.parlor.Log;
import com.threerings.parlor.data.ParlorCodes;
import com.threerings.parlor.server.ParlorSender;

/**
 * The game manager handles the server side management of a game. It
 * manipulates the game state in accordance with the logic of the game
 * flow and generally manages the whole game playing process.
 *
 * <p> The game manager extends the place manager because games are
 * implicitly played in a location, the players of the game implicitly
 * bodies in that location.
 */
public class GameManager extends PlaceManager
    implements ParlorCodes, GameProvider, AttributeChangeListener
{
    // documentation inherited
    protected void didInit ()
    {
        super.didInit();

        // save off a casted reference to our config
        _gameconfig = (GameConfig)_config;
    }

    /**
     * Adds the given player to the game at the first available player
     * index.  This should only be called before the game is started, and
     * is most likely to be used to add players to party games.
     *
     * @param player the username of the player to add to this game.
     * @return the player index at which the player was added, or
     * <code>-1</code> if the player could not be added to the game.
     */
    public int addPlayer (String player)
    {
        // determine the first available player index
        int pidx = -1;
        int size = _gameobj.players.length;
        for (int ii = 0; ii < size; ii++) {
            if (!_gameobj.isOccupiedPlayer(ii)) {
                pidx = ii;
                break;
            }
        }

        // sanity-check the player index
        if (pidx == -1) {
            Log.warning("Couldn't find free player index for player  " +
                        "[game=" + _gameobj.which() + ", player=" + player +
                        ", players=" + StringUtil.toString(_gameobj.players) +
                        "].");
            return -1;
        }

        // proceed with the rest of the adding business
        return (!addPlayerAt(player, pidx)) ? -1 : pidx;
    }

    /**
     * Adds the given player to the game at the specified player index.
     * This should only be called before the game is started, and is most
     * likely to be used to add players to party games.
     *
     * @param player the username of the player to add to this game.
     * @param pidx the player index at which the player is to be added.
     * @return true if the player was added successfully, false if not.
     */
    public boolean addPlayerAt (String player, int pidx)
    {
        // make sure the specified player index is valid
        if (pidx < 0 || pidx >= _gameobj.players.length) {
            Log.warning("Attempt to add player at an invalid index " +
                        "[game=" + _gameobj.which() + ", player=" + player +
                        ", pidx=" + pidx + "].");
            return false;
        }

        // make sure the player index is available
        if (_gameobj.players[pidx] != null) {
            Log.warning("Attempt to add player at occupied index " +
                        "[game=" + _gameobj.which() + ", player=" + player +
                        ", pidx=" + pidx + "].");
            return false;
        }

        // make sure the player isn't already somehow a part of the game
        // to avoid any potential badness that might ensue if we added
        // them more than once
        if (_gameobj.getPlayerIndex(player) != -1) {
            Log.warning("Attempt to add player to game that they're already " +
                        "playing [game=" + _gameobj.which() +
                        ", player=" + player + "].");
            return false;
        }

        // get the player's body object
        BodyObject bobj = CrowdServer.lookupBody(player);
        if (bobj == null) {
            Log.warning("Unable to get body object while adding player " +
                        "[game=" + _gameobj.which() +
                        ", player=" + player + "].");
            return false;
        }

        // fill in the player's information
        _gameobj.setPlayersAt(player, pidx);

        // increment the number of players in the game
        _playerCount++;

        // save off their oid
        _playerOids[pidx] = bobj.getOid();

        // let derived classes do what they like
        playerWasAdded(player, pidx);

        return true;
    }

    /**
     * Called when a player was added to the game.  Derived classes may
     * override this method to perform any game-specific actions they
     * desire, but should be sure to call
     * <code>super.playerWasAdded()</code>.
     *
     * @param player the username of the player added to the game.
     * @param pidx the player index of the player added to the game.
     */
    protected void playerWasAdded (String player, int pidx)
    {
    }

    /**
     * Removes the given player from the game.  This is most likely to be
     * used to allow players involved in a party game to leave the game
     * early-on if they realize they'd rather not play for some reason.
     *
     * @param player the username of the player to remove from this game.
     * @return true if the player was successfully removed, false if not.
     */
    public boolean removePlayer (String player)
    {
        // get the player's index in the player list
        int pidx = _gameobj.getPlayerIndex(player);

        // sanity-check the player index
        if (pidx == -1) {
            Log.warning("Attempt to remove non-player from players list " +
                        "[game=" + _gameobj.which() +
                        ", player=" + player +
                        ", players=" + StringUtil.toString(_gameobj.players) +
                        "].");
            return false;
        }

        // remove the player from the players list
        _gameobj.setPlayersAt(null, pidx);

        // clear out the player's entry in the player oid list
        _playerOids[pidx] = 0;

        if (_AIs != null) {
            // clear out the player's entry in the AI list
            _AIs[pidx] = -1;
        }

        // decrement the number of players in the game
        _playerCount--;

        // let derived classes do what they like
        playerWasRemoved(player, pidx);

        return true;
    }

    /**
     * Called when a player was removed from the game.  Derived classes
     * may override this method to perform any game-specific actions they
     * desire, but should be sure to call
     * <code>super.playerWasRemoved()</code>.
     *
     * @param player the username of the player removed from the game.
     * @param pidx the player index of the player before they were removed
     * from the game.
     */
    protected void playerWasRemoved (String player, int pidx)
    {
    }

    /**
     * Sets the specified player as an AI with the specified skill.  It is
     * assumed that this will be set soon after the player names for all
     * AIs present in the game. (It should be done before human players
     * start trickling into the game.)
     *
     * @param pidx the player index of the AI.
     * @param skill the skill level, from 0 to 100 inclusive.
     */
    public void setAI (final int pidx, final byte skill)
    {
        if (_AIs == null) {
            // create and initialize the AI skill level array
            _AIs = new byte[_gameobj.players.length];
            Arrays.fill(_AIs, (byte)-1);
            // set up a delegate op for AI ticking
            _tickAIOp = new TickAIDelegateOp();
        }

        // save off the AI's skill level
        _AIs[pidx] = skill;

        // let the delegates know that the player's been made an AI
        applyToDelegates(new DelegateOp() {
            public void apply (PlaceManagerDelegate delegate) {
                ((GameManagerDelegate)delegate).setAI(pidx, skill);
            }
        });
    }

    /**
     * Returns the name of the player with the specified index.
     */
    public String getPlayerName (int index)
    {
        return _gameobj.players[index];
    }

    /**
     * Returns the player index of the given user in the game, or 
     * <code>-1</code> if the player is not involved in the game.
     */
    public int getPlayerIndex (String username)
    {
        return _gameobj.getPlayerIndex(username);
    }

    /**
     * Returns the user object oid of the player with the specified index.
     */
    public int getPlayerOid (int index)
    {
        return _playerOids[index];
    }

    /**
     * Returns the number of players in the game.
     */
    public int getPlayerCount ()
    {
        return _playerCount;
    }

    /**
     * Returns whether the player at the specified player index is an AI.
     */
    public boolean isAI (int pidx)
    {
        return (_AIs != null && _AIs[pidx] != -1);
    }

    /**
     * Returns the unique round identifier for the current round.
     */
    public int getRoundId ()
    {
        return _gameobj.roundId;
    }

    /**
     * Sends a system message to the players in the game room.
     */
    public void systemMessage (String msgbundle, String msg)
    {
        systemMessage(msgbundle, msg, false);
    }

    /**
     * Sends a system message to the players in the game room.
     *
     * @param waitForStart if true, the message will not be sent until the
     * game has started.
     */
    public void systemMessage (
        String msgbundle, String msg, boolean waitForStart)
    {
        if (waitForStart &&
            ((_gameobj == null) ||
             (_gameobj.state == GameObject.AWAITING_PLAYERS))) {
            // queue up the message.
            if (_startmsgs == null) {
                _startmsgs = new ArrayList();
            }
            _startmsgs.add(msgbundle);
            _startmsgs.add(msg);
            return;
        }

        // otherwise, just deliver the message
        SpeakProvider.sendSystemSpeak(_gameobj, msgbundle, msg);
    }

    // documentation inherited
    protected Class getPlaceObjectClass ()
    {
        return GameObject.class;
    }

    // documentation inherited
    protected void didStartup ()
    {
        // obtain a casted reference to our game object
        _gameobj = (GameObject)_plobj;

        // stick the players into the game object
        _gameobj.setPlayers(_gameconfig.players);

        // save off the number of players so that we needn't repeatedly
        // iterate through the player name array server-side unnecessarily
        _playerCount = _gameobj.getPlayerCount();

        // instantiate a player oid array which we'll fill in later
        int size = _gameobj.players.length;
        _playerOids = new int[size];

        // create and fill in our game service object
        GameMarshaller service = (GameMarshaller)
            _invmgr.registerDispatcher(new GameDispatcher(this), false);
        _gameobj.setGameService(service);

        // give delegates a chance to do their thing
        super.didStartup();

        // let the players of this game know that we're ready to roll (if
        // we have a specific set of players)
        for (int ii = 0; ii < size; ii++) {
            // skip non-existent players
            if (!_gameobj.isOccupiedPlayer(ii)) {
                continue;
            }

            BodyObject bobj = CrowdServer.lookupBody(_gameobj.players[ii]);
            if (bobj == null) {
                Log.warning("Unable to deliver game ready to non-existent " +
                            "player [game=" + _gameobj.which() +
                            ", player=" + _gameobj.players[ii] + "].");
                continue;
            }

            // deliver a game ready notification to the player
            ParlorSender.gameIsReady(bobj, _gameobj.getOid());
        }
    }

    // documentation inherited
    protected void didShutdown ()
    {
        super.didShutdown();

        // clear out our service registration
        _invmgr.clearDispatcher(_gameobj.gameService);
    }

    // documentation inherited
    protected void bodyLeft (int bodyOid)
    {
        super.bodyLeft(bodyOid);

        // TODO: if this is a party game not yet in play and the creator
        // left, choose a new creator if at least one player still remains

        // deal with disappearing players
    }

    /**
     * When a game room becomes empty, we cancel the game if it's still in
     * progress and close down the game room.
     */
    protected void placeBecameEmpty ()
    {
//         Log.info("Game room empty. Going away. " +
//                  "[game=" + _gameobj.which() + "].");

        // cancel the game if it was in play
        if (_gameobj.state == GameObject.IN_PLAY) {
            _gameobj.setState(GameObject.CANCELLED);
        }

        // shut down the place (which will destroy the game object and
        // clean up after everything)
        shutdown();
    }

    /**
     * Called when all players have arrived in the game room. By default,
     * this starts up the game, but a manager may wish to override this
     * and start the game according to different criterion.
     */
    protected void playersAllHere ()
    {
        // start up the game if we're not a party game and if we haven't
        // already done so
        if (!_gameconfig.isPartyGame() &&
            _gameobj.state == GameObject.AWAITING_PLAYERS) {
            startGame();
        }
    }

    /**
     * This is called when the game is ready to start (all players
     * involved have delivered their "am ready" notifications). It calls
     * {@link #gameWillStart}, sets the necessary wheels in motion and
     * then calls {@link #gameDidStart}.  Derived classes should override
     * one or both of the calldown functions (rather than this function)
     * if they need to do things before or after the game starts.
     *
     * @return true if the game was started, false if it could not be
     * started because it was already in play or because all players have
     * not yet reported in.
     */
    public boolean startGame ()
    {
        // complain if we're already started
        if (_gameobj.state == GameObject.IN_PLAY) {
            Log.warning("Requested to start an already in-play game " +
                        "[game=" + _gameobj + "].");
            return false;
        }

        // make sure everyone has turned up
        if (!allPlayersReady()) {
            Log.warning("Requested to start a game that is still " +
                        "awaiting players [game=" + _gameobj.which() +
                        ", pnames=" + StringUtil.toString(_gameobj.players) +
                        ", poids=" + StringUtil.toString(_playerOids) + "].");
            return false;
        }

        // let the derived class do its pre-start stuff
        gameWillStart();

        // transition the game to started
        _gameobj.setState(GameObject.IN_PLAY);

        // do post-start processing
        gameDidStart();

        return true;
    }

    /**
     * Called when the game is about to start, but before the game start
     * notification has been delivered to the players. Derived classes
     * should override this if they need to perform some pre-start
     * activities.
     */
    protected void gameWillStart ()
    {
        // increment the round identifier
        _gameobj.setRoundId(_gameobj.roundId + 1);

        // let our delegates do their business
        applyToDelegates(new DelegateOp() {
            public void apply (PlaceManagerDelegate delegate) {
                ((GameManagerDelegate)delegate).gameWillStart();
            }
        });
    }

    /**
     * Called after the game start notification was dispatched.  Derived
     * classes can override this to put whatever wheels they might need
     * into motion now that the game is started (if anything other than
     * transitioning the game to {@link GameObject#IN_PLAY} is necessary).
     */
    protected void gameDidStart ()
    {
        // let our delegates do their business
        applyToDelegates(new DelegateOp() {
            public void apply (PlaceManagerDelegate delegate) {
                ((GameManagerDelegate)delegate).gameDidStart();
            }
        });

        // inform the players of any pending messages.
        if (_startmsgs != null) {
            for (Iterator iter = _startmsgs.iterator(); iter.hasNext(); ) {
                systemMessage((String) iter.next(), // bundle
                              (String) iter.next()); // message
            }
            _startmsgs = null;
        }

        // and register ourselves to receive AI ticks
        if (_AIs != null) {
            AIGameTicker.registerAIGame(this);
        }
    }

    /**
     * Called by the {@link AIGameTicker} if we're registered as an AI
     * game.
     */
    protected void tickAIs ()
    {
        for (int ii = 0; ii < _AIs.length; ii++) {
            byte level = _AIs[ii];
            if (level != -1) {
                tickAI(ii, level);
            }
        }
    }

    /**
     * Called by tickAIs to tick each AI in the game.
     */
    protected void tickAI (int pidx, byte level)
    {
        _tickAIOp.setAI(pidx, level);
        applyToDelegates(_tickAIOp);
    }

    /**
     * Called when the game is known to be over. This will call some
     * calldown functions to determine the winner of the game and then
     * transition the game to the {@link GameObject#GAME_OVER} state.
     */
    public void endGame ()
    {
        if (_gameobj.state != GameObject.IN_PLAY) {
            Log.debug("Refusing to end game that was not in play " +
                      "[game=" + _gameobj.which() + "].");
            return;
        }

        // figure out who won...

        // transition to the game over state
        _gameobj.setState(GameObject.GAME_OVER);

        // wait until we hear the game state transition on the game object
        // to invoke our game over code so that we can be sure that any
        // final events dispatched on the game object prior to the call to
        // endGame() have been dispatched
    }

    /**
     * Called after the game has transitioned to the {@link
     * GameObject#GAME_OVER} state. Derived classes should override this
     * to perform any post-game activities.
     */
    protected void gameDidEnd ()
    {
        // remove ourselves from the AI ticker, if applicable
        if (_AIs != null) {
            AIGameTicker.unregisterAIGame(this);
        }

        // let our delegates do their business
        applyToDelegates(new DelegateOp() {
            public void apply (PlaceManagerDelegate delegate) {
                ((GameManagerDelegate)delegate).gameDidEnd();
            }
        });

        // calculate ratings and all that...
    }

    /**
     * Called when the game is to be reset to its starting state in
     * preparation for a new game without actually ending the current
     * game. It calls {@link #gameWillReset} and {@link #gameDidReset}.
     * The standard game start processing ({@link #gameWillStart} and
     * {@link #gameDidStart}) will also be called (in between the calls to
     * will and did reset). Derived classes should override one or both of
     * the calldown functions (rather than this function) if they need to
     * do things before or after the game resets.
     */
    public void resetGame ()
    {
        // let the derived class do its pre-reset stuff
        gameWillReset();

        // do the standard game start processing
        gameWillStart();
        _gameobj.setState(GameObject.IN_PLAY);
        gameDidStart();

        // let the derived class do its post-reset stuff
        gameDidReset();
    }

    /**
     * Called when the game is about to reset, but before the board has
     * been re-initialized or any other clearing out of game data has
     * taken place.  Derived classes should override this if they need to
     * perform some pre-reset activities.
     */
    protected void gameWillReset ()
    {
        // let our delegates do their business
        applyToDelegates(new DelegateOp() {
            public void apply (PlaceManagerDelegate delegate) {
                ((GameManagerDelegate)delegate).gameWillReset();
            }
        });
    }

    /**
     * Called after the game has been reset.  Derived classes can override
     * this to put whatever wheels they might need into motion now that
     * the game is reset.
     */
    protected void gameDidReset ()
    {
        // let our delegates do their business
        applyToDelegates(new DelegateOp() {
            public void apply (PlaceManagerDelegate delegate) {
                ((GameManagerDelegate)delegate).gameDidReset();
            }
        });
    }

    // documentation inherited from interface
    public void playerReady (ClientObject caller)
    {
        BodyObject plobj = (BodyObject)caller;

        // make a note of this player's oid
        int pidx = _gameobj.getPlayerIndex(plobj.username);
        if (pidx == -1) {
            Log.warning("Received playerReady() from non-player? " +
                        "[caller=" + caller + "].");
            return;
        }
        _playerOids[pidx] = plobj.getOid();

        // if everyone is now ready to go, get things underway
        if (allPlayersReady()) {
            playersAllHere();
        }
    }

    /**
     * Returns true if all (non-AI) players have delivered their {@link
     * #playerReady} notifications, false if they have not.
     */
    protected boolean allPlayersReady ()
    {
        int pcount = _gameobj.players.length;
        for (int ii = 0; ii < pcount; ii++) {
            if (_gameobj.isOccupiedPlayer(ii) &&
                (_playerOids[ii] == 0) &&
                (_AIs == null || _AIs[ii] == -1)) {
                return false;
            }
        }
        return true;
    }

    // documentation inherited from interface
    public void startPartyGame (ClientObject caller)
    {
        // make sure this is a party game
        if (!_gameconfig.isPartyGame()) {
            Log.warning("Attempt to player-start a non-party game " +
                        "[game=" + _gameobj.which() +
                        ", caller=" + caller + "].");
            return;
        }

        // make sure the caller is the creating player
        BodyObject plobj = (BodyObject)caller;
        int pidx = _gameobj.getPlayerIndex(plobj.username);
        if (pidx != _gameobj.creator) {
            Log.warning("Attempt to start party game by non-creating player " +
                        "[game=" + _gameobj.which() +
                        ", creator=" + getPlayerName(_gameobj.creator) +
                        ", caller=" + caller + "].");
            return;
        }

        // make sure the game is ready to go
        if (!canStartPartyGame()) {
            Log.warning("Attempt to start party game that can't yet begin " +
                        "[game=" + _gameobj.which() +
                        ", caller=" + caller + "].");
            return;
        }

        // start things up
        startGame();
    }

    /**
     * Returns whether this party game is all set to begin.  The default
     * implementation returns true.  Derived classes that implement a
     * party game should override this method to enforce any prerequisites
     * (such as a minimum number of players) as appropriate.
     */
    protected boolean canStartPartyGame ()
    {
        return true;
    }

    // documentation inherited
    public void attributeChanged (AttributeChangedEvent event)
    {
        if (event.getName().equals(GameObject.STATE)) {
            switch (event.getIntValue()) {
            case GameObject.CANCELLED:
                // fall through to GAME_OVER case
            case GameObject.GAME_OVER:
                // now we do our end of game processing
                gameDidEnd();
                break;
            }
        }
    }

    /**
     * A helper operation to distribute AI ticks to our delegates.
     */
    protected class TickAIDelegateOp implements DelegateOp
    {
        public void apply (PlaceManagerDelegate delegate) {
            ((GameManagerDelegate) delegate).tickAI(_pidx, _level);
        }

        public void setAI (int pidx, byte level)
        {
            _pidx = pidx;
            _level = level;
        }

        protected int _pidx;
        protected byte _level;
    }

    /** A reference to our game config. */
    protected GameConfig _gameconfig;

    /** A reference to our game object. */
    protected GameObject _gameobj;

    /** The number of players in the game. */
    protected int _playerCount;

    /** The oids of our player and AI body objects. */
    protected int[] _playerOids;

    /** If AIs are present, contains their skill levels, or -1 at human
     * player indexes. */
    protected byte[] _AIs;

    /** If non-null, contains bundles and messages that should be sent as
     * system messages once the game has started. */
    protected ArrayList _startmsgs;

    /** Our delegate operator to tick AIs. */
    protected TickAIDelegateOp _tickAIOp;
}
