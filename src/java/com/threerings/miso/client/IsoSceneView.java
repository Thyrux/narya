//
// $Id: IsoSceneView.java,v 1.137 2003/04/12 02:14:52 mdb Exp $

package com.threerings.miso.client;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.event.MouseEvent;

import java.util.ArrayList;
import java.util.List;

import com.samskivert.util.HashIntMap;
import com.samskivert.util.RuntimeAdjust;
import com.samskivert.util.StringUtil;

import com.threerings.media.RegionManager;

import com.threerings.media.sprite.Sprite;
import com.threerings.media.sprite.SpriteManager;
import com.threerings.media.util.Path;

import com.threerings.media.tile.ObjectTile;
import com.threerings.media.tile.Tile;

import com.threerings.miso.Log;
import com.threerings.miso.MisoPrefs;
import com.threerings.miso.client.DirtyItemList.DirtyItem;
import com.threerings.miso.client.util.AStarPathUtil;
import com.threerings.miso.client.util.IsoUtil;
import com.threerings.miso.client.util.ObjectSet;
import com.threerings.miso.tile.BaseTile;

/**
 * The iso scene view provides an isometric view of a particular scene. It
 * presently supports scrolling in a limited form. Object tiles are not
 * handled properly, nor is mouse highlighting. Those should only be used
 * if the view will not be scrolled.
 */
public class IsoSceneView implements SceneView
{
    /** Instructs the scene view never to draw highlights around object
     * tiles. */
    public static final int HIGHLIGHT_NEVER = 0;

    /** Instructs the scene view to highlight only object tiles that have
     * a non-empty action string. */
    public static final int HIGHLIGHT_WITH_ACTION = 1;

    /** Instructs the scene view to highlight every object tile,
     * regardless of whether it has a valid action string. */
    public static final int HIGHLIGHT_ALWAYS = 2;

    /** Instructs the scene view to highlight whatever tile the mouse is
     * over, regardless of whether or not it is an object tile. This is
     * generally only useful in an editor rather than a game. */
    public static final int HIGHLIGHT_ALL = 3;

    /**
     * Constructs an iso scene view.
     *
     * @param spritemgr the sprite manager.
     * @param model the data model.
     * @param remgr the region manager that is collecting invalid regions
     * for this view.
     */
    public IsoSceneView (SpriteManager spritemgr, IsoSceneViewModel model,
                         RegionManager remgr)
    {
        // save off references
        _spritemgr = spritemgr;
        _model = model;
        _remgr = remgr;

        // handy rectangle
        _tbounds = new Rectangle(0, 0, _model.tilewid, _model.tilehei);
    }

    /**
     * Configures the scene view to highlight object tiles either never
     * ({@link #HIGHLIGHT_NEVER}), only when an object tile has an
     * associated action string ({@link #HIGHLIGHT_WITH_ACTION}), or
     * always ({@link #HIGHLIGHT_ALWAYS}). It is also possible to
     * configure the view to highlight whatever tile is under the cursor,
     * even if it's not an object tile which is done in the {@link
     * #HIGHLIGHT_ALL} mode.
     */
    public void setHighlightMode (int hmode)
    {
        _hmode = hmode;
    }

    // documentation inherited
    public void setScene (DisplayMisoScene scene)
    {
        _scene = scene;
        _scene.init();

        // obtain a list of the objects in the scene and generate records
        // for each of them that contain precomputed metrics
        refreshObjectList();

        // invalidate the entire screen as there's a new scene in town;
        // making sure we're on the AWT thread
        if (EventQueue.isDispatchThread()) {
            invalidate();
        } else {
            EventQueue.invokeLater(new Runnable() {
                public void run () {
                    invalidate();
                }
            });
        }
    }

    /**
     * Big fat HACKola: checks the display scene for new objects that
     * weren't there last time around and adds info for them into the
     * scene.
     */
    public void refreshObjectList ()
    {
        _objects.clear();

        // grab all available objects
        Rectangle rect = new Rectangle(Short.MIN_VALUE, Short.MIN_VALUE,
                                       2*Short.MAX_VALUE, 2*Short.MAX_VALUE);
        _scene.getObjects(rect, _objects);
        addAdditionalObjects(_objects);

        // and fill in the new objects' bounds
        int ocount = _objects.size();
        for (int ii = 0; ii < ocount; ii++) {
            DisplayObjectInfo scobj = (DisplayObjectInfo)_objects.get(ii);
            if (scobj.bounds == null) {
                scobj.bounds = IsoUtil.getObjectBounds(_model, scobj);
            }
        }
    }

    // documentation inherited
    public DisplayMisoScene getScene ()
    {
        return _scene;
    }

    /**
     * Invalidate the entire visible scene view.
     */
    public void invalidate ()
    {
        _remgr.invalidateRegion(_model.bounds);
    }

    /**
     * Returns the location associated with the specified object's "spot"
     * in fine coordinates or null if the object has no spot.
     */
    public Point getObjectSpot (DisplayObjectInfo info)
    {
        if (info.tile.hasSpot()) {
            return IsoUtil.tilePlusFineToFull(
                _model, info.x, info.y, info.tile.getSpotX(),
                info.tile.getSpotY(), new Point());
        } else {
            return null;
        }
    }

    // documentation inherited from interface
    public void paint (Graphics2D gfx, Rectangle dirtyRect)
    {
        if (_scene == null) {
            Log.warning("Scene view painted with null scene.");
            return;
        }

        // render any intersecting tiles
        renderTiles(gfx, dirtyRect);

        // render anything that goes on top of the tiles
        renderBaseDecorations(gfx, dirtyRect);

        // render our dirty sprites and objects
        renderDirtyItems(gfx, dirtyRect);

        // draw sprite paths
        if (_pathsDebug.getValue()) {
            _spritemgr.renderSpritePaths(gfx);
        }

        // paint our highlighted tile (if any)
        paintHighlights(gfx, dirtyRect);

        // paint any extra goodies
        paintExtras(gfx, dirtyRect);
    }

    /**
     * Paints the highlighted tile.
     *
     * @param gfx the graphics context.
     */
    protected void paintHighlights (Graphics2D gfx, Rectangle clip)
    {
        // if we're not highlighting anything, bail now
        if (_hmode == HIGHLIGHT_NEVER) {
            return;
        }

        Polygon hpoly = null;

        // if we have a hover object, do some business
        if (_hobject != null && _hobject instanceof DisplayObjectInfo) {
            DisplayObjectInfo scobj = (DisplayObjectInfo)_hobject;
            if (scobj.action != null || _hmode == HIGHLIGHT_ALWAYS) {
                hpoly = IsoUtil.getObjectFootprint(_model, scobj);
            }
        }

        // if we had no valid hover object, but we're in HIGHLIGHT_ALWAYS,
        // go for the tile outline
        if (hpoly == null && _hmode == HIGHLIGHT_ALWAYS) {
            hpoly = IsoUtil.getTilePolygon(_model, _hcoords.x, _hcoords.y);
        }

        if (hpoly != null) {
            // set the desired stroke and color
            Stroke ostroke = gfx.getStroke();
            gfx.setStroke(_hstroke);
            gfx.setColor(Color.green);

            // draw the outline
            gfx.draw(hpoly);

            // restore the original stroke
            gfx.setStroke(ostroke);
        }
    }

    /**
     * A function where derived classes can paint extra stuff while we've
     * got the clipping region set up.
     */
    protected void paintExtras (Graphics2D gfx, Rectangle clip)
    {
        // nothing for now
    }

    /**
     * Renders the base and fringe layer tiles that intersect the
     * specified clipping rectangle.
     */
    protected void renderTiles (Graphics2D gfx, Rectangle clip)
    {
        // if we're showing coordinates, we need to do some setting up
        int thw = 0, thh = 0, fhei = 0;
        FontMetrics fm = null;
        if (_coordsDebug.getValue()) {
            fm = gfx.getFontMetrics(_font);
            fhei = fm.getAscent();
            thw = _model.tilehwid;
            thh = _model.tilehhei;
            gfx.setFont(_font);
        }

        // determine which tiles intersect this clipping region: this is
        // going to be nearly incomprehensible without some sort of
        // diagram; i'll do what i can to comment it, but you'll want to
        // print out a scene diagram (docs/miso/scene.ps) and start making
        // notes if you want to follow along

        // obtain our upper left tile
        Point tpos = IsoUtil.screenToTile(_model, clip.x, clip.y, new Point());

        // determine which quadrant of the upper left tile we occupy
        Point spos = IsoUtil.tileToScreen(_model, tpos.x, tpos.y, new Point());
        boolean left = (clip.x - spos.x < _model.tilehwid);
        boolean top = (clip.y - spos.y < _model.tilehhei);

        // set up our tile position counters
        int dx, dy;
        if (left) {
            dx = 0; dy = 1;
        } else {
            dx = 1; dy = 0;
        }

        // if we're in the top-half of the tile we need to move up a row,
        // either forward or back depending on whether we're in the left
        // or right half of the tile
        if (top) {
            if (left) {
                tpos.x -= 1;
            } else {
                tpos.y -= 1;
            }
            // we'll need to start zig-zagging the other way as well
            dx = 1 - dx;
            dy = 1 - dy;
        }

        // these will bound our loops
        int rightx = clip.x + clip.width, bottomy = clip.y + clip.height;

//         Log.info("Preparing to render [tpos=" + StringUtil.toString(tpos) +
//                  ", left=" + left + ", top=" + top +
//                  ", clip=" + StringUtil.toString(clip) +
//                  ", spos=" + StringUtil.toString(spos) +
//                  "].");

        // obtain the coordinates of the tile that starts the first row
        // and loop through, rendering the intersecting tiles
        IsoUtil.tileToScreen(_model, tpos.x, tpos.y, spos);
        while (spos.y < bottomy) {
            // set up our row counters
            int tx = tpos.x, ty = tpos.y;
            _tbounds.x = spos.x;
            _tbounds.y = spos.y;

//             Log.info("Rendering row [tx=" + tx + ", ty=" + ty + "].");

            // render the tiles in this row
            while (_tbounds.x < rightx) {
                // draw the base and fringe tile images
                try {
                    Tile tile;
                    boolean passable = true;

                    if ((tile = _scene.getBaseTile(tx, ty)) != null) {
                        tile.paint(gfx, _tbounds.x, _tbounds.y);
                        passable = ((BaseTile)tile).isPassable();

                        // highlight impassable tiles
                        if (_traverseDebug.getValue() && !passable) {
                            fillTile(gfx, tx, ty, Color.yellow);
                        }

                    } else {
                        // draw black where there are no tiles
                        Polygon poly = IsoUtil.getTilePolygon(_model, tx, ty);
                        gfx.setColor(Color.black);
                        gfx.fill(poly);
                    }

                    if ((tile = _scene.getFringeTile(tx, ty)) != null) {
                        tile.paint(gfx, _tbounds.x, _tbounds.y);
                    }

                    // highlight passable non-traversable tiles
                    if (_traverseDebug.getValue() && passable && 
                        !_scene.canTraverse(null, tx, ty)) {
                        fillTile(gfx, tx, ty, Color.green);
                    }

                } catch (ArrayIndexOutOfBoundsException e) {
                    Log.warning("Whoops, booched it [tx=" + tx +
                                ", ty=" + ty + ", tb.x=" + _tbounds.x +
                                ", rightx=" + rightx + "].");
                }

                // if we're showing coordinates, do that
                if (_coordsDebug.getValue()) {
                    gfx.setColor(Color.white);

                    // get the top-left screen coordinates of the tile
                    int sx = _tbounds.x, sy = _tbounds.y;

                    // draw x-coordinate
                    String str = String.valueOf(tx);
                    int xpos = sx + thw - (fm.stringWidth(str) / 2);
                    gfx.drawString(str, xpos, sy + thh);

                    // draw y-coordinate
                    str = String.valueOf(ty);
                    xpos = sx + thw - (fm.stringWidth(str) / 2);
                    gfx.drawString(str, xpos, sy + thh + fhei);

                    // draw the tile polygon as well
                    gfx.draw(IsoUtil.getTilePolygon(_model, tx, ty));
                }

                // move one tile to the right
                tx += 1; ty -= 1;
                _tbounds.x += _model.tilewid;
            }

            // update our tile coordinates
            tpos.x += dx; dx = 1-dx;
            tpos.y += dy; dy = 1-dy;

            // obtain the screen coordinates of the next starting tile
            IsoUtil.tileToScreen(_model, tpos.x, tpos.y, spos);
        }
    }

    /**
     * Fills the specified tile with the given color at 50% alpha.
     * Intended for debug-only tile highlighting purposes.
     */
    protected void fillTile (
        Graphics2D gfx, int tx, int ty, Color color)
    {
        Composite ocomp = gfx.getComposite();
        gfx.setComposite(ALPHA_FILL_TILE);
        Polygon poly = IsoUtil.getTilePolygon(_model, tx, ty);
        gfx.setColor(color);
        gfx.fill(poly);
        gfx.setComposite(ocomp);
    }

    /**
     * A function where derived classes can paint things after the base
     * tiles have been rendered but before anything else has been rendered
     * (so that whatever is painted appears to be on the ground).
     */
    protected void renderBaseDecorations (Graphics2D gfx, Rectangle clip)
    {
        // nothing for now
    }

    /**
     * Renders the dirty sprites and objects in the scene to the given
     * graphics context.
     */
    protected void renderDirtyItems (Graphics2D gfx, Rectangle clip)
    {
        // if we don't yet have a scene, do nothing
        if (_scene == null) {
            return;
        }

        // add any sprites impacted by the dirty rectangle
        _dirtySprites.clear();
        _spritemgr.getIntersectingSprites(_dirtySprites, clip);
        int size = _dirtySprites.size();
        for (int ii = 0; ii < size; ii++) {
            Sprite sprite = (Sprite)_dirtySprites.get(ii);
            Rectangle bounds = sprite.getBounds();
            if (!bounds.intersects(clip)) {
                continue;
            }
            appendDirtySprite(_dirtyItems, sprite);
            // Log.info("Dirtied item: " + sprite);
        }

        // add any objects impacted by the dirty rectangle
        int ocount = _objects.size();
        for (int ii = 0; ii < ocount; ii++) {
            DisplayObjectInfo scobj = (DisplayObjectInfo)_objects.get(ii);
            if (!scobj.bounds.intersects(clip)) {
                continue;
            }

            // compute the footprint if we're rendering those
            Polygon foot = null;
            if (_fprintDebug.getValue()) {
                foot = IsoUtil.getObjectFootprint(_model, scobj);
            }

            _dirtyItems.appendDirtyObject(scobj, foot);
            // Log.info("Dirtied item: Object(" +
            // scobj.x + ", " + scobj.y + ")");
        }

//         Log.info("renderDirtyItems [items=" + _dirtyItems.size() + "].");

        // sort the dirty sprites and objects visually back-to-front;
        // paint them and be done
        _dirtyItems.sort();
        _dirtyItems.paintAndClear(gfx);
    }

    /**
     * Computes the tile coordinates of the supplied sprite and appends it
     * to the supplied dirty item list.
     */
    protected void appendDirtySprite (DirtyItemList list, Sprite sprite)
    {
        IsoUtil.screenToTile(_model, sprite.getX(), sprite.getY(), _tcoords);
        list.appendDirtySprite(sprite, _tcoords.x, _tcoords.y);
    }

    /**
     * A place for subclasses to add any additional scene objects they
     * may have.
     */
    protected void addAdditionalObjects (ObjectSet set)
    {
        // nothing by default
    }

    // documentation inherited
    public Path getPath (Sprite sprite, int x, int y)
    {
        // make sure we have a scene
        if (_scene == null) {
            return null;
        }

        // get the destination tile coordinates
        Point src = IsoUtil.screenToTile(
            _model, sprite.getX(), sprite.getY(), new Point());
        Point dest = IsoUtil.screenToTile(_model, x, y, new Point());

        // TODO: compute this value from the screen size or something
        int longestPath = 50;

        // get a reasonable tile path through the scene
        List points = AStarPathUtil.getPath(
            _scene, sprite, longestPath, src.x, src.y, dest.x, dest.y);

        // construct a path object to guide the sprite on its merry way
        return (points == null) ? null :
            new TilePath(_model, sprite, points, x, y);
    }

    // documentation inherited
    public Point getScreenCoords (int x, int y)
    {
        return IsoUtil.fullToScreen(_model, x, y, new Point());
    }

    // documentation inherited
    public Point getFullCoords (int x, int y)
    {
        return IsoUtil.screenToFull(_model, x, y, new Point());
    }

    // documentation inherited
    public Point getTileCoords (int x, int y)
    {
        return IsoUtil.screenToTile(_model, x, y, new Point());
    }

    // documentation inherited
    public boolean mouseMoved (MouseEvent e)
    {
        int x = e.getX(), y = e.getY();
        boolean repaint = false;

        // update the mouse's tile coordinates
        boolean newtile = updateTileCoords(x, y, _hcoords);
        // if we're highlighting base tiles, we may need to repaint
        if (_hmode == HIGHLIGHT_ALL) {
            repaint = (newtile || repaint);
        }

        // compute the list of objects over which the mouse is hovering
        Object hobject = null;

        // start with the sprites that contain the point
        _spritemgr.getHitSprites(_hitSprites, x, y);
        int hslen = _hitSprites.size();
        for (int i = 0; i < hslen; i++) {
            Sprite sprite = (Sprite)_hitSprites.get(i);
            appendDirtySprite(_hitList, sprite);
        }

        // add the object tiles that contain the point
        getHitObjects(_hitList, x, y);

        // sort the list of hit items by rendering order
        _hitList.sort();

        // the last element in the array is what we want (assuming there
        // are any items in the array)
        int icount = _hitList.size();
        if (icount > 0) {
            DirtyItem item = (DirtyItem)_hitList.get(icount-1);
            hobject = item.obj;
        }

        repaint |= changeHoverObject(hobject);

        // clear out the hitlists
        _hitList.clear();
        _hitSprites.clear();

        return repaint;
    }

    /**
     * Change the hover object to the new object.
     *
     * @return true if we need to repaint the entire scene. Bah!
     */
    protected boolean changeHoverObject (Object newHover)
    {
        if (newHover == _hobject) {
            return false; // no change, no repaint
        }

        // unhover the old
        if (_hobject instanceof DisplayObjectInfo) {
            DisplayObjectInfo oldhov = (DisplayObjectInfo) _hobject;
            if (oldhov.setHovered(false)) {
                _remgr.invalidateRegion(oldhov.bounds);
            }
        }

        hoverObjectChanged(_hobject, newHover);
        // set the new 
        _hobject = newHover;

        // hover the new
        if (_hobject instanceof DisplayObjectInfo) {
            DisplayObjectInfo newhov = (DisplayObjectInfo) _hobject;
            if (newhov.setHovered(true)) {
                _remgr.invalidateRegion(newhov.bounds);
            }
        }

        return (_hmode != HIGHLIGHT_NEVER);
    }

    /**
     * A place for subclasses to react to the hover object changing.
     * One of the supplied arguments may be null.
     */
    protected void hoverObjectChanged (Object oldHover, Object newHover)
    {
        // nothing by default
    }

    /**
     * Adds to the supplied dirty item list, all of the object tiles that
     * are hit by the specified point (meaning the point is contained
     * within their bounds and intersects a non-transparent pixel in the
     * actual object image.
     */
    protected void getHitObjects (DirtyItemList list, int x, int y)
    {
        int ocount = _objects.size();
        for (int ii = 0; ii < ocount; ii++) {
            DisplayObjectInfo scobj = (DisplayObjectInfo)_objects.get(ii);
            Rectangle pbounds = scobj.bounds;
            // skip bounding rects that don't contain the point
            if (!pbounds.contains(x, y)) {
                continue;
            }

            // now check that the pixel in the tile image is
            // non-transparent at that point
            if (!scobj.tile.hitTest(x - pbounds.x, y - pbounds.y)) {
                continue;
            }

            // we've passed the test, add the object to the list
            list.appendDirtyObject(scobj, null);
        }
    }

    // documentation inherited
    public void mouseExited (MouseEvent e)
    {
        // clear the highlight tracking data
        _hcoords.setLocation(-1, -1);
        changeHoverObject(null);
    }

    // documentation inherited
    public Object getHoverObject ()
    {
        return _hobject;
    }

    /**
     * Returns the tile coordinates of the tile over which the mouse is
     * hovering.
     */
    public Point getHoverCoords ()
    {
        return _hcoords;
    }

    /**
     * Converts the supplied screen coordinates into tile coordinates,
     * writing the values into the supplied {@link Point} instance and
     * returning true if the screen coordinates translated into a
     * different set of tile coordinates than were already contained in
     * the point (so that the caller can know to update a highlight, for
     * example).
     *
     * @return true if the tile coordinates have changed.
     */
    protected boolean updateTileCoords (int sx, int sy, Point tpos)
    {
        Point npos = IsoUtil.screenToTile(_model, sx, sy, new Point());

        // make sure the new coordinate is both valid and different
        if (_model.isCoordinateValid(npos.x, npos.y) && !tpos.equals(npos)) {
            tpos.setLocation(npos.x, npos.y);
            return true;

        } else {
            return false;
        }
    }

    /** The sprite manager. */
    protected SpriteManager _spritemgr;

    /** The scene view model data. */
    protected IsoSceneViewModel _model;

    /** Our region manager. */
    protected RegionManager _remgr;

    /** The scene to be displayed. */
    protected DisplayMisoScene _scene;

    /** Information for all of the objects visible in the scene. */
    protected ObjectSet _objects = new ObjectSet();

    /** The dirty sprites and objects that need to be re-painted. */
    protected DirtyItemList _dirtyItems = new DirtyItemList(this);

    /** The working sprites list used when calculating dirty regions. */
    protected ArrayList _dirtySprites = new ArrayList();

    /** Used when rendering tiles. */
    protected Rectangle _tbounds;

    /** Used when dirtying sprites. */
    protected Point _tcoords = new Point();

    /** Used to collect the list of sprites "hit" by a particular mouse
     * location. */
    protected List _hitSprites = new ArrayList();

    /** The list that we use to track and sort the items over which the
     * mouse is hovering. */
    protected DirtyItemList _hitList = new DirtyItemList(this);

    /** The highlight mode. */
    protected int _hmode = HIGHLIGHT_NEVER;

    /** Info on the object that the mouse is currently hovering over. */
    protected Object _hobject;

    /** Used to track the tile coordinates over which the mouse is hovering. */
    protected Point _hcoords = new Point();

    /** The font to draw tile coordinates. */
    protected Font _font = new Font("Arial", Font.PLAIN, 7);

    /** The stroke object used to draw highlighted tiles and coordinates. */
    protected BasicStroke _hstroke = new BasicStroke(2);

    /** A debug hook that toggles debug rendering of traversable tiles. */
    protected static RuntimeAdjust.BooleanAdjust _traverseDebug =
        new RuntimeAdjust.BooleanAdjust(
            "Toggles debug rendering of traversable and impassable tiles in " +
            "the iso scene view.", "narya.miso.iso_traverse_debug_render",
            MisoPrefs.config, false);

    /** A debug hook that toggles debug rendering of tile coordinates. */
    protected static RuntimeAdjust.BooleanAdjust _coordsDebug =
        new RuntimeAdjust.BooleanAdjust(
            "Toggles debug rendering of tile coordinates in the iso scene " +
            "view.", "narya.miso.iso_coords_debug_render",
            MisoPrefs.config, false);

    /** A debug hook that toggles debug rendering of sprite paths. */
    protected static RuntimeAdjust.BooleanAdjust _pathsDebug =
        new RuntimeAdjust.BooleanAdjust(
            "Toggles debug rendering of sprite paths in the iso scene view.",
            "narya.miso.iso_paths_debug_render", MisoPrefs.config, false);

    /** A debug hook that toggles debug rendering of object footprints. */
    protected static RuntimeAdjust.BooleanAdjust _fprintDebug =
        new RuntimeAdjust.BooleanAdjust(
            "Toggles debug rendering of object footprints in the iso scene " +
            "view.", "narya.miso.iso_fprint_debug_render",
            MisoPrefs.config, false);

    /** The stroke used to draw dirty rectangles. */
    protected static final Stroke DIRTY_RECT_STROKE = new BasicStroke(2);

    /** The alpha used to fill tiles for debugging purposes. */
    protected static final Composite ALPHA_FILL_TILE =
        AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f);
}
