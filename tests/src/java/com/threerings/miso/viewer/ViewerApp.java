//
// $Id: ViewerApp.java,v 1.40 2004/02/25 14:51:26 mdb Exp $

package com.threerings.miso.viewer;

import java.awt.DisplayMode;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.io.IOException;

import com.samskivert.swing.util.SwingUtil;

import com.threerings.resource.ResourceManager;

import com.threerings.media.FrameManager;
import com.threerings.media.image.ImageManager;
import com.threerings.media.tile.bundle.BundledTileSetRepository;

import com.threerings.cast.CharacterManager;
import com.threerings.cast.bundle.BundledComponentRepository;

import com.threerings.miso.Log;
import com.threerings.miso.data.SimpleMisoSceneModel;
import com.threerings.miso.tile.MisoTileManager;
import com.threerings.miso.tools.xml.SimpleMisoSceneParser;
import com.threerings.miso.util.MisoContext;

/**
 * The ViewerApp is a scene viewing application that allows for trying
 * out game scenes in a pseudo-runtime environment.
 */
public class ViewerApp
{
    /**
     * Construct and initialize the ViewerApp object.
     */
    public ViewerApp (String[] args)
        throws IOException
    {
        if (args.length < 1) {
            System.err.println("Usage: ViewerApp scene_file.xml");
            System.exit(-1);
        }

        // get the graphics environment
        GraphicsEnvironment env =
            GraphicsEnvironment.getLocalGraphicsEnvironment();

        // get the target graphics device
        GraphicsDevice gd = env.getDefaultScreenDevice();
        Log.info("Graphics device [dev=" + gd +
                 ", mem=" + gd.getAvailableAcceleratedMemory() +
                 ", displayChange=" + gd.isDisplayChangeSupported() +
                 ", fullScreen=" + gd.isFullScreenSupported() + "].");

        // get the graphics configuration and display mode information
        GraphicsConfiguration gc = gd.getDefaultConfiguration();
        DisplayMode dm = gd.getDisplayMode();
        Log.info("Display mode [bits=" + dm.getBitDepth() +
                 ", wid=" + dm.getWidth() + ", hei=" + dm.getHeight() +
                 ", refresh=" + dm.getRefreshRate() + "].");

        // create the window
	_frame = new ViewerFrame(gc);
        _framemgr = FrameManager.newInstance(_frame);

        // we don't need to configure anything
        ResourceManager rmgr = new ResourceManager("rsrc");
        rmgr.initBundles(
            null, "config/resource/manager.properties", null, null);
        ImageManager imgr = new ImageManager(rmgr, _frame);
	_tilemgr = new MisoTileManager(rmgr, imgr);
        _tilemgr.setTileSetRepository(
            new BundledTileSetRepository(rmgr, imgr, "tilesets"));

	// create the context object
	MisoContext ctx = new ContextImpl();

        // create the various managers
        BundledComponentRepository crepo =
            new BundledComponentRepository(rmgr, imgr, "components");
        CharacterManager charmgr = new CharacterManager(imgr, crepo);

        // create our scene view panel
        _panel = new ViewerSceneViewPanel(ctx, charmgr, crepo);
        _frame.setPanel(_panel);

        // load up the scene specified by the user
        try {
            SimpleMisoSceneParser parser = new SimpleMisoSceneParser("");
            SimpleMisoSceneModel model = parser.parseScene(args[0]);
            if (model == null) {
                Log.warning("No miso scene found in scene file " +
                            "[path=" + args[0] + "].");
                System.exit(-1);
            }
            _panel.setSceneModel(model);

        } catch (Exception e) {
            Log.warning("Unable to parse scene [path=" + args[0] + "].");
            Log.logStackTrace(e);
            System.exit(-1);
        }

        // size and position the window, entering full-screen exclusive
        // mode if available
        if (gd.isFullScreenSupported()) {
            Log.info("Entering full-screen exclusive mode.");
            gd.setFullScreenWindow(_frame);

        } else {
            Log.warning("Full-screen exclusive mode not available.");
            // _frame.pack();
            _frame.setSize(600, 400);
            SwingUtil.centerWindow(_frame);
        }
    }

    /**
     * The implementation of the MisoContext interface that provides
     * handles to the config and manager objects that offer commonly used
     * services.
     */
    protected class ContextImpl implements MisoContext
    {
	public MisoTileManager getTileManager ()
	{
	    return _tilemgr;
	}

        public FrameManager getFrameManager ()
        {
            return _framemgr;
        }
    }

    /**
     * Run the application.
     */
    public void run ()
    {
        // show the window
        _frame.show();
        _framemgr.start();
    }

    /**
     * Instantiate the application object and start it running.
     */
    public static void main (String[] args)
    {
        try {
            ViewerApp app = new ViewerApp(args);
            app.run();
        } catch (IOException ioe) {
            System.err.println("Error initializing viewer app.");
            ioe.printStackTrace();
        }
    }

    /** The tile manager object. */
    protected MisoTileManager _tilemgr;

    /** The frame manager. */
    protected FrameManager _framemgr;

    /** The main application window. */
    protected ViewerFrame _frame;

    /** The main panel. */
    protected ViewerSceneViewPanel _panel;
}
