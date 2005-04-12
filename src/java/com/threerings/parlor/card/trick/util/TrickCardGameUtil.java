//
// $Id: TrickCardGameObject.java 3382 2005-03-03 19:55:35Z mdb $
//
// Narya library - tools for developing networked games
// Copyright (C) 2002-2004 Three Rings Design, Inc., All Rights Reserved
// http://www.threerings.net/code/narya/
//
// This library is free software; you can redistribute it and/or modify it
// under the terms of the GNU Lesser General Public License as published
// by the Free Software Foundation; either version 2.1 of the License, or
// (at your option) any later version.
//
// This library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
// Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public
// License along with this library; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA

package com.threerings.parlor.card.trick.util;

import com.threerings.parlor.card.data.Card;
import com.threerings.parlor.card.data.Hand;
import com.threerings.parlor.card.data.PlayerCard;
import com.threerings.parlor.card.trick.data.TrickCardCodes;

/**
 * Methods of general utility to trick-taking card games.
 */
public class TrickCardGameUtil
    implements TrickCardCodes
{
    /**
     * For four-player games with fixed partnerships, this returns the index
     * of the player's team.
     *
     * @param plidx the player index
     */
    public static int getTeamIndex (int plidx)
    {
        return plidx / 2;
    }
    
    /**
     * For four-player games with fixed partnerships, this returns the index
     * of the other team.
     *
     * @param tidx the index of the team
     */
    public static int getOtherTeamIndex (int tidx)
    {
        return 1 - tidx;
    }
    
    /**
     * For four-player games with fixed partnerships, this returns the index
     * of the player's partner.
     */
    public static int getPartnerIndex (int plidx)
    {
        return plidx ^ 1;
    }
    
    /**
     * For four-player games with fixed partnerships, this returns the index
     * of one of the members of a team.
     *
     * @param tidx the index of the team
     * @param midx the index of the player within the team
     */
    public static int getTeamMemberIndex (int tidx, int midx)
    {
        return tidx * 2 + midx;
    }
    
    /**
     * For four-player games with fixed partnerships, this returns the index
     * of the player after the specified player going clockwise around the
     * table.
     */
    public static int getNextInClockwiseSequence (int plidx)
    {
        //   0
        // 2   3
        //   1
        switch (plidx) {
            case 0: return 3;
            case 1: return 2;
            case 2: return 0;
            case 3: return 1;
            default: return -1;
        }
    }
    
    /**
     * For four-player games with fixed partnerships, this returns the
     * relative location of one player from the point of view of another.
     *
     * @param pidx1 the index of the player to whom the location is relative
     * @param pidx2 the index of the player whose location is desired
     * @return the relative location (TOP, BOTTOM, LEFT, or RIGHT)
     */
    public static int getRelativeLocation (int pidx1, int pidx2)
    {
        return RELATIVE_LOCATIONS[pidx1][pidx2];
    }
    
    /**
     * Checks whether the player can follow the suit lead with the hand given.
     */
    public static boolean canFollowSuit (PlayerCard[] cardsPlayed, Hand hand)
    {
        return hand.getSuitMemberCount(cardsPlayed[0].card.getSuit()) > 0;
    }
    
    /**
     * Determines the number of cards that belong to the specified suit within
     * the array given.
     */
    public static int countSuitMembers (PlayerCard[] cards, int suit)
    {
        int count = 0;
        for (int i = 0; i < cards.length; i++) {
            if (cards[i].card.getSuit() == suit) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * Checks whether the proposed card follows the suit lead.
     */
    public static boolean followsSuit (PlayerCard[] cardsPlayed, Card card)
    {
        return cardsPlayed[0].card.getSuit() == card.getSuit();
    }
    
    /**
     * Returns the highest card (according to the standard A,K,...,2 ordering)
     * in the suit lead, with an optional trump suit.
     *
     * @param trumpSuit the trump suit, or -1 for none
     */
    public static PlayerCard getHighestInLeadSuit (PlayerCard[] cardsPlayed,
        int trumpSuit)
    {
        PlayerCard highest = cardsPlayed[0];
        for (int i = 1; i < cardsPlayed.length; i++) {
            PlayerCard other = cardsPlayed[i];
            if ((other.card.getSuit() == highest.card.getSuit() &&
                    other.card.compareTo(highest.card) > 0) ||
                (other.card.getSuit() == trumpSuit &&
                    highest.card.getSuit() != trumpSuit)) {
                highest = other;
            }
        }
        return highest;
    }
    
    /** The locations of the other players for each player index. */
    protected static final int[][] RELATIVE_LOCATIONS = {
        {BOTTOM, TOP, RIGHT, LEFT}, {TOP, BOTTOM, LEFT, RIGHT},
        {LEFT, RIGHT, BOTTOM, TOP}, {RIGHT, LEFT, TOP, BOTTOM} };
}