//
// $Id: GameObject.java,v 1.3 2002/02/20 23:35:42 mdb Exp $

package com.threerings.parlor.game;

import com.samskivert.util.StringUtil;
import com.threerings.crowd.data.PlaceObject;

/**
 * A game object hosts the shared data associated with a game played by
 * one or more players. The game object extends the place object so that
 * the game can act as a place where players actually go when playing the
 * game. Only very basic information is maintained in the base game
 * object. It serves as the base for a hierarchy of game object
 * derivatives that handle basic gameplay for a suite of different game
 * types (ie. turn based games, party games, board games, card games,
 * etc.).
 */
public class GameObject extends PlaceObject
{
    /** The field name of the <code>state</code> field. */
    public static final String STATE = "state";

    /** The field name of the <code>isRated</code> field. */
    public static final String IS_RATED = "isRated";

    /** The field name of the <code>players</code> field. */
    public static final String PLAYERS = "players";

    /** A game state constant indicating that the game has not yet started
     * and is still awaiting the arrival of all of the players. */
    public static final int AWAITING_PLAYERS = 0;

    /** A game state constant indicating that the game is in play. */
    public static final int IN_PLAY = AWAITING_PLAYERS+1;

    /** A game state constant indicating that the game ended normally. */
    public static final int GAME_OVER = IN_PLAY+2;

    /** A game state constant indicating that the game was cancelled. */
    public static final int CANCELLED = GAME_OVER+3;

    /** The game state, one of {@link #AWAITING_PLAYERS}, {@link #IN_PLAY},
     * {@link #GAME_OVER}, or {@link #CANCELLED}. */
    public int state;

    /** Indicates whether or not this game is rated. */
    public boolean isRated;

    /** The username of the players involved in this game. */
    public String[] players;

    /**
     * Requests that the <code>state</code> field be set to the specified
     * value. The local value will be updated immediately and an event
     * will be propagated through the system to notify all listeners that
     * the attribute did change. Proxied copies of this object (on
     * clients) will apply the value change when they received the
     * attribute changed notification.
     */
    public void setState (int state)
    {
        this.state = state;
        requestAttributeChange(STATE, new Integer(state));
    }

    /**
     * Requests that the <code>isRated</code> field be set to the specified
     * value. The local value will be updated immediately and an event
     * will be propagated through the system to notify all listeners that
     * the attribute did change. Proxied copies of this object (on
     * clients) will apply the value change when they received the
     * attribute changed notification.
     */
    public void setIsRated (boolean isRated)
    {
        this.isRated = isRated;
        requestAttributeChange(IS_RATED, new Boolean(isRated));
    }

    /**
     * Requests that the <code>players</code> field be set to the specified
     * value. The local value will be updated immediately and an event
     * will be propagated through the system to notify all listeners that
     * the attribute did change. Proxied copies of this object (on
     * clients) will apply the value change when they received the
     * attribute changed notification.
     */
    public void setPlayers (String[] players)
    {
        this.players = players;
        requestAttributeChange(PLAYERS, players);
    }
}
