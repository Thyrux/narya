//
// $Id: IsoSceneViewModel.java,v 1.29 2003/04/12 02:14:52 mdb Exp $

package com.threerings.miso.client;

import java.awt.Point;
import java.awt.Rectangle;

import com.threerings.miso.Log;
import com.threerings.miso.MisoConfig;
import com.threerings.miso.client.util.IsoUtil;

/**
 * Provides a holding place for the myriad parameters and bits of data
 * that describe the details of an isometric view of a scene.
 *
 * <p> The member data are public to facilitate speedy referencing by the
 * {@link IsoSceneView} class.  The model should only be modified through
 * the configuration data provided via {@link MisoConfig} and the accessor
 * methods.
 */
public class IsoSceneViewModel
{
    /** Tile dimensions and half-dimensions in the view. */
    public int tilewid, tilehei, tilehwid, tilehhei;

    /** Fine coordinate dimensions. */
    public int finehwid, finehhei;

    /** Number of fine coordinates on each axis within a tile. */
    public int finegran;

    /** Size of the view in tile count. */
    public int scenevwid, scenevhei;

    /** Whether or not this view can extend beyond the bounds defined by
     * the view width and height. True if it cannot, false if it can. */
    public boolean bounded = false;

    /** The bounds of the view in screen pixel coordinates. */
    public Rectangle bounds;

    /** The position in pixels at which tile (0, 0) is drawn. */ 
    public Point origin;

    /** The length of a tile edge in pixels. */
    public float tilelen;

    /** The slope of the x- and y-axis lines. */
    public float slopeX, slopeY;

    /** The length between fine coordinates in pixels. */
    public float finelen;

    /** The y-intercept of the x-axis line within a tile. */
    public float fineBX;

    /** The slope of the x- and y-axis lines within a tile. */
    public float fineSlopeX, fineSlopeY;

    /**
     * Construct an iso scene view model with view parameters as
     * specified in the given config object.
     *
     * @param config the config object.
     */
    public IsoSceneViewModel ()
    {
        // and the view dimensions
	scenevwid = MisoConfig.config.getValue(
            SCENE_VWIDTH_KEY, DEF_SCENE_VWIDTH);
	scenevhei = MisoConfig.config.getValue(
            SCENE_VHEIGHT_KEY, DEF_SCENE_VHEIGHT);

	// get the tile dimensions
	tilewid = MisoConfig.config.getValue(TILE_WIDTH_KEY, DEF_TILE_WIDTH);
	tilehei = MisoConfig.config.getValue(TILE_HEIGHT_KEY, DEF_TILE_HEIGHT);

	// set the fine coordinate granularity
	finegran = MisoConfig.config.getValue(FINE_GRAN_KEY, DEF_FINE_GRAN);

        // precalculate various things
	int offy = MisoConfig.config.getValue(SCENE_OFFSET_Y_KEY, DEF_OFFSET_Y);
        precalculate(offy);
    }

    /**
     * Constructs an iso scene view model by directly specifying the
     * desired scene configuration parameters.
     *
     * @param scenewid the total scene width in tiles.
     * @param scenehei the total scene height in tiles.
     * @param tilewid the width in pixels of the tiles.
     * @param tilehei the height in pixels of the tiles.
     * @param finegran the number of sub-tile divisions to use for fine
     * coordinates.
     * @param svwid the width in tiles of the viewport.
     * @param svhei the height in tiles of the viewport.
     * @param offy the offset of the origin (in tiles) from the top of the
     * viewport.
     */
    public IsoSceneViewModel (int scenewid, int scenehei,
                              int tilewid, int tilehei, int finegran,
                              int svwid, int svhei, int offy)
    {
        // keep track of this stuff
        this.tilewid = tilewid;
        this.tilehei = tilehei;
        this.finegran = finegran;
        this.scenevwid = svwid;
        this.scenevhei = svhei;

        // let our flags default to false

        // precalculate various things
        precalculate(offy);
    }

    /**
     * Returns whether the given tile coordinate is a valid coordinate in
     * our coordinate system (which allows tile coordinates from 0 to
     * 2^15-1).
     */
    public boolean isCoordinateValid (int x, int y)
    {
        return (x >= 0 && x < Short.MAX_VALUE &&
                y >= 0 && y < Short.MAX_VALUE);
    }

    /**
     * Returns whether the given full coordinate is a valid coordinate
     * within the scene.
     */
    public boolean isFullCoordinateValid (int x, int y)
    {
        int tx = IsoUtil.fullToTile(x), ty = IsoUtil.fullToTile(y);
        int fx = IsoUtil.fullToFine(x), fy = IsoUtil.fullToFine(y);
        return (isCoordinateValid(tx, ty) &&
                fx >= 0 && fx < finegran &&
                fy >= 0 && fy < finegran);
    }

    /**
     * Pre-calculate various member data that are commonly used in working
     * with an isometric view.
     */
    protected void precalculate (int offy)
    {
	// set the desired scene view bounds
	bounds = new Rectangle(0, 0, scenevwid * tilewid, scenevhei * tilehei);

	// set the scene display origin
        origin = new Point((bounds.width / 2), (offy * tilehei));

	// pre-calculate tile-related data
	precalculateTiles();

	// calculate the edge length separating each fine coordinate
	finelen = tilelen / (float)finegran;

	// calculate the fine-coordinate x-axis line
	fineSlopeX = (float)tilehei / (float)tilewid;
	fineBX = -(fineSlopeX * (float)tilehwid);
	fineSlopeY = -fineSlopeX;

	// calculate the fine coordinate dimensions
	finehwid = (int)((float)tilehwid / (float)finegran);
	finehhei = (int)((float)tilehhei / (float)finegran);
    }

    /**
     * Pre-calculate various tile-related member data.
     */
    protected void precalculateTiles ()
    {
        // halve the dimensions
        tilehwid = (tilewid / 2);
        tilehhei = (tilehei / 2);

        // calculate the length of a tile edge in pixels
        tilelen = (float) Math.sqrt(
            (tilehwid * tilehwid) + (tilehhei * tilehhei));

        // calculate the slope of the x- and y-axis lines
        slopeX = (float)tilehei / (float)tilewid;
        slopeY = -slopeX;
    }

    /** The config key for tile width in pixels. */
    protected static final String TILE_WIDTH_KEY = "tile_width";

    /** The config key for tile height in pixels. */
    protected static final String TILE_HEIGHT_KEY = "tile_height";

    /** The config key for tile fine coordinate granularity. */
    protected static final String FINE_GRAN_KEY = "fine_granularity";

    /** The config key for scene view width in tile count. */
    protected static final String SCENE_VWIDTH_KEY = "scene_view_width";

    /** The config key for scene view height in tile count. */
    protected static final String SCENE_VHEIGHT_KEY = "scene_view_height";

    /** The config key for scene width in tile count. */
    protected static final String SCENE_WIDTH_KEY = "scene_width";

    /** The config key for scene height in tile count. */
    protected static final String SCENE_HEIGHT_KEY = "scene_height";

    /** The config key for scene origin vertical offset in tile count. */
    protected static final String SCENE_OFFSET_Y_KEY = "scene_offset_y";

    /** Default scene view parameters. */
    protected static final int DEF_TILE_WIDTH = 64;
    protected static final int DEF_TILE_HEIGHT = 48;
    protected static final int DEF_FINE_GRAN = 4;
    protected static final int DEF_SCENE_VWIDTH = 10;
    protected static final int DEF_SCENE_VHEIGHT = 12;
    protected static final int DEF_SCENE_WIDTH = 22;
    protected static final int DEF_SCENE_HEIGHT = 22;
    protected static final int DEF_OFFSET_Y = -5;
}
