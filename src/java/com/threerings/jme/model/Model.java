//
// $Id$
//
// Narya library - tools for developing networked games
// Copyright (C) 2002-2005 Three Rings Design, Inc., All Rights Reserved
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

package com.threerings.jme.model;

import java.io.Externalizable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Properties;

import com.samskivert.util.ObserverList;

import com.jme.bounding.BoundingVolume;
import com.jme.math.FastMath;
import com.jme.math.Matrix4f;
import com.jme.math.Quaternion;
import com.jme.math.Vector3f;
import com.jme.renderer.Camera;
import com.jme.renderer.CloneCreator;
import com.jme.renderer.Renderer;
import com.jme.scene.Controller;
import com.jme.scene.Node;
import com.jme.scene.Spatial;

import com.threerings.util.RandomUtil;

import com.threerings.jme.Log;

/**
 * The base node for models.
 */
public class Model extends ModelNode
{
    /** Lets listeners know when animations are completed (which only happens
     * for non-repeating animations) or cancelled. */
    public interface AnimationObserver
    {
        /**
         * Called when an animation has started.
         *
         * @return true to remain on the observer list, false to remove self
         */
        public boolean animationStarted (Model model, String anim);
        
        /**
         * Called when a non-repeating animation has finished.
         *
         * @return true to remain on the observer list, false to remove self
         */
        public boolean animationCompleted (Model model, String anim);
        
        /**
         * Called when an animation has been cancelled.
         *
         * @return true to remain on the observer list, false to remove self
         */
        public boolean animationCancelled (Model model, String anim);
    }
    
    /** An animation for the model. */
    public static class Animation
        implements Serializable
    {
        /** The rate of the animation in frames per second. */
        public int frameRate;
        
        /** The animation repeat type ({@link Controller#RT_CLAMP},
         * {@link Controller#RT_CYCLE}, or {@link Controller#RT_WRAP}). */
        public int repeatType;
        
        /** The transformation targets of the animation. */
        public Spatial[] transformTargets;
        
        /** The animation transforms (one transform per target per frame). */
        public transient Transform[][] transforms;
        
        /**
         * Returns this animation's duration in seconds.
         */
        public float getDuration ()
        {
            return (float)transforms.length / frameRate;
        }
        
        /**
         * Rebinds this animation for a prototype instance.
         *
         * @param pnodes a mapping from prototype nodes to instance nodes
         */
        public Animation rebind (HashMap pnodes)
        {
            Animation anim = new Animation();
            anim.frameRate = frameRate;
            anim.repeatType = repeatType;
            anim.transforms = transforms;
            anim.transformTargets = new Spatial[transformTargets.length];
            for (int ii = 0; ii < transformTargets.length; ii++) {
                anim.transformTargets[ii] =
                    (Spatial)pnodes.get(transformTargets[ii]);
            }
            return anim;
        }
        
        private void writeObject (ObjectOutputStream out)
            throws IOException
        {
            out.defaultWriteObject();
            out.writeInt(transforms.length);
            for (int ii = 0; ii < transforms.length; ii++) {
                for (int jj = 0; jj < transformTargets.length; jj++) {
                    transforms[ii][jj].writeExternal(out);
                }
            }
        }

        private void readObject (ObjectInputStream in)
            throws IOException, ClassNotFoundException
        {
            in.defaultReadObject();
            transforms = new Transform[in.readInt()][transformTargets.length];
            for (int ii = 0; ii < transforms.length; ii++) {
                for (int jj = 0; jj < transformTargets.length; jj++) {
                    transforms[ii][jj] = new Transform(new Vector3f(),
                        new Quaternion(), new Vector3f());
                    transforms[ii][jj].readExternal(in);
                }
            }
        }
        
        private static final long serialVersionUID = 1;
    }
    
    /** A frame element that manipulates the target's transform. */
    public static final class Transform
        implements Externalizable
    {
        public Transform (
            Vector3f translation, Quaternion rotation, Vector3f scale)
        {
            _translation = translation;
            _rotation = rotation;
            _scale = scale;
        }
        
        public void apply (Spatial target)
        {
            target.getLocalTranslation().set(_translation);
            target.getLocalRotation().set(_rotation);
            target.getLocalScale().set(_scale);
        }
        
        /**
         * Blends between this transform and the next, applying the result to
         * the given target.
         *
         * @param alpha the blend factor: 0.0 for entirely this frame, 1.0 for
         * entirely the next
         */
        public void blend (Transform next, float alpha, Spatial target)
        {
            target.getLocalTranslation().interpolate(_translation,
                next._translation, alpha);
            target.getLocalRotation().slerp(_rotation, next._rotation, alpha);
            target.getLocalScale().interpolate(_scale, next._scale, alpha);
        }
        
        // documentation inherited from interface Externalizable
        public void writeExternal (ObjectOutput out)
            throws IOException
        {
            _translation.writeExternal(out);
            _rotation.writeExternal(out);
            _scale.writeExternal(out);
        }
        
        // documentation inherited from interface Externalizable
        public void readExternal (ObjectInput in)
            throws IOException, ClassNotFoundException
        {
            _translation.readExternal(in);
            _rotation.readExternal(in);
            _scale.readExternal(in);
        }

        /** The transform at this frame. */
        protected Vector3f _translation, _scale;
        protected Quaternion _rotation;
        
        private static final long serialVersionUID = 1;
    }
    
    /**
     * Attempts to read a model from the specified file.
     *
     * @param map if true, map buffers into memory directly from the
     * file
     */
    public static Model readFromFile (File file, boolean map)
        throws IOException
    {
        // read the serialized model and its children
        FileInputStream fis = new FileInputStream(file);
        ObjectInputStream ois = new ObjectInputStream(fis);
        Model model;
        try {
            model = (Model)ois.readObject();
        } catch (ClassNotFoundException e) {
            Log.warning("Encountered unknown class [error=" + e + "].");
            return null;
        }
        
        // then either read or map the buffers
        FileChannel fc = fis.getChannel();
        if (map) {
            long pos = fc.position();
            model.sliceBuffers(fc.map(FileChannel.MapMode.READ_ONLY,
                pos, fc.size() - pos));
        } else {
            model.readBuffers(fc);
        }
        ois.close();
        
        // initialize the model as a prototype
        model.initPrototype();
        
        return model;
    }
    
    /**
     * No-arg constructor for deserialization.
     */
    public Model ()
    {
    }
    
    /**
     * Standard constructor.
     */
    public Model (String name, Properties props)
    {
        super(name);
        _props = props;
    }
    
    /**
     * Returns a reference to the properties of the model.
     */
    public Properties getProperties ()
    {
        return _props;
    }
    
    /**
     * Initializes this model as prototype.  Only necessary when the prototype
     * was not loaded through {@link #readFromFile}.
     */
    public void initPrototype ()
    {
        setReferenceTransforms();
        cullInvisibleNodes();
        initInstance();
    }
    
    /**
     * Adds an animation to the model's library.  This should only be called by
     * the model compiler.
     */
    public void addAnimation (String name, Animation anim)
    {
        if (_anims == null) {
            _anims = new HashMap<String, Animation>();
        }
        _anims.put(name, anim);
        
        // store the original transforms
        Transform[] oxforms = new Transform[anim.transformTargets.length];
        for (int ii = 0; ii < anim.transformTargets.length; ii++) {
            Spatial target = anim.transformTargets[ii];
            oxforms[ii] = new Transform(
                new Vector3f(target.getLocalTranslation()),
                new Quaternion(target.getLocalRotation()),
                new Vector3f(target.getLocalScale()));
        }
        
        // run through every frame of the animation, expanding the bounding
        // volumes of any deformable meshes
        for (int ii = 0; ii < anim.transforms.length; ii++) {
            for (int jj = 0; jj < anim.transforms[ii].length; jj++) {
                anim.transforms[ii][jj].apply(anim.transformTargets[jj]);
            }
            updateWorldData(0f);
            expandModelBounds();
        }
        
        // restore the original transforms
        for (int ii = 0; ii < anim.transformTargets.length; ii++) {
            oxforms[ii].apply(anim.transformTargets[ii]);
        }
        updateWorldData(0f);
    }
    
    /**
     * Returns the names of the model's animations.
     */
    public String[] getAnimationNames ()
    {
        if (_prototype != null) {
            return _prototype.getAnimationNames();
        }
        return (_anims == null) ? new String[0] :
            _anims.keySet().toArray(new String[_anims.size()]);
    }
    
    /**
     * Checks whether the unit has an animation with the given name.
     */
    public boolean hasAnimation (String name)
    {
        if (_prototype != null) {
            return _prototype.hasAnimation(name);
        }
        return (_anims == null) ? false : _anims.containsKey(name);
    }
    
    /**
     * Starts the named animation.
     *
     * @return the duration of the started animation (for looping animations,
     * the duration of one cycle), or -1 if the animation was not found
     */
    public float startAnimation (String name)
    {
        Animation anim = getAnimation(name);
        if (anim == null) {
            return -1f;
        }
        if (_anim != null) {
            _animObservers.apply(new AnimCancelledOp(_animName));
        }
        _anim = anim;
        _animName = name;
        _fidx = 0;
        _nidx = 1;
        _fdir = +1;
        _elapsed = 0f;
        _animObservers.apply(new AnimStartedOp(_animName));
        return anim.getDuration() / _animSpeed;
    }
    
    /**
     * Fast-forwards the current animation by the given number of seconds.
     */
    public void fastForwardAnimation (float time)
    {
        updateAnimation(time);
    }
    
    /**
     * Gets a reference to the animation with the given name.
     */
    public Animation getAnimation (String name)
    {
        if (_anims == null) {
            return null;
        }
        Animation anim = _anims.get(name);
        if (anim != null) {
            return anim;
        }
        if (_prototype != null) {
            Animation panim = _prototype._anims.get(name);
            if (panim != null) {
                _anims.put(name, anim = panim.rebind(_pnodes));
                return anim;
            }
        }
        Log.warning("Requested unknown animation [name=" + name + "].");
        return null;
    }
    
    /**
     * Stops the currently running animation.
     */
    public void stopAnimation ()
    {
        if (_anim == null) {
            return;
        }
        _anim = null;
        _animObservers.apply(new AnimCancelledOp(_animName));
    }
    
    /**
     * Sets the animation speed, which acts as a multiplier for the frame rate
     * of each animation.
     */
    public void setAnimationSpeed (float speed)
    {
        _animSpeed = speed;
    }
    
    /**
     * Returns the currently configured animation speed.
     */
    public float getAnimationSpeed ()
    {
        return _animSpeed;
    }
    
    /**
     * Adds an animation observer.
     */
    public void addAnimationObserver (AnimationObserver obs)
    {
        _animObservers.add(obs);
    }
    
    /**
     * Removes an animation observer.
     */
    public void removeAnimationObserver (AnimationObserver obs)
    {
        _animObservers.remove(obs);
    }
    
    /**
     * Returns a reference to the node that contains this model's emissions
     * (in world space, so the emissions do not move with the model).  This
     * node is created and added when this method is first called.
     */
    public Node getEmissionNode ()
    {
        if (_emissionNode == null) {
            attachChild(_emissionNode = new Node("emissions"));
            _emissionNode.setTransformable(false);
        }
        return _emissionNode;
    }
    
    /**
     * Writes this model out to a file.
     */
    public void writeToFile (File file)
        throws IOException
    {
        // start out by writing this node and its children
        FileOutputStream fos = new FileOutputStream(file);
        ObjectOutputStream oos = new ObjectOutputStream(fos);
        oos.writeObject(this);
        
        // now traverse the scene graph appending buffers to the
        // end of the file
        writeBuffers(fos.getChannel());
        oos.close();
    }
    
    @Override // documentation inherited
    public void writeExternal (ObjectOutput out)
        throws IOException
    {
        // don't serialize the emission node; it contains transient geometry
        // created by controllers
        if (_emissionNode != null) {
            detachChild(_emissionNode);
        }
        super.writeExternal(out);
        if (_emissionNode != null) {
            attachChild(_emissionNode);
        }
        out.writeObject(_props);
        out.writeObject(_anims);
        out.writeObject(getControllers());
    }
    
    @Override // documentation inherited
    public void readExternal (ObjectInput in)
        throws IOException, ClassNotFoundException
    {
        super.readExternal(in);
        _props = (Properties)in.readObject();
        _anims = (HashMap<String, Animation>)in.readObject();
        ArrayList controllers = (ArrayList)in.readObject();
        for (Object ctrl : controllers) {
            addController((Controller)ctrl);
        }
    }

    @Override // documentation inherited
    public void resolveTextures (TextureProvider tprov)
    {
        super.resolveTextures(tprov);
        for (Object ctrl : getControllers()) {
            if (ctrl instanceof ModelController) {
                ((ModelController)ctrl).resolveTextures(tprov);
            }
        }
    }
    
    /**
     * Creates and returns a new instance of this model's default
     * variant.
     */
    public Model createInstance ()
    {
        return createInstance(null);
    }
    
    /**
     * Creates and returns a new instance of this model.
     *
     * @param variant the name of the variant desired, or <code>null</code>
     * for the default variant
     */    
    public Model createInstance (String variant)
    {
        if (_prototype != null) {
            return _prototype.createInstance(variant);
        }
        if (_ccreator == null) {
            _ccreator = new ModelCloneCreator(this);
        }
        _ccreator.variant = variant;
        Model instance = (Model)_ccreator.createCopy();
        instance.initInstance();
        return instance;
    }

    /**
     * Locks the transforms and bounds of this model in the expectation that it
     * will never be moved from its current position.
     */
    public void lockInstance ()
    {
        // collect the controller targets and lock recursively
        HashSet<Spatial> targets = new HashSet<Spatial>();
        for (Object ctrl : getControllers()) {
            if (ctrl instanceof ModelController) {
                targets.add(((ModelController)ctrl).getTarget());
            }
        }
        lockInstance(targets);
    }
    
    @Override // documentation inherited
    public Spatial putClone (Spatial store, CloneCreator properties)
    {
        Model mstore = (Model)properties.originalToCopy.get(this);
        if (mstore != null) {
            return mstore;
        } else if (store == null) {
            mstore = new Model(getName(), _props);
        } else {
            mstore = (Model)store;
        }
        // don't clone the emission node, as it contains transient geometry
        if (_emissionNode != null) {
            detachChild(_emissionNode);
        }
        super.putClone(mstore, properties);
        if (_emissionNode != null) {
            attachChild(_emissionNode);
        }
        mstore._prototype = this;
        if (_anims != null) {
            mstore._anims = new HashMap<String, Animation>();
        }
        mstore._pnodes = (HashMap)properties.originalToCopy.clone();
        return mstore;
    }
    
    @Override // documentation inherited
    public void updateGeometricState (float time, boolean initiator)
    {
        // if we were not visible the last time we were rendered, don't do a
        // full update; just update the world bound and wait until we come
        // into view
        boolean wasOutside = _outside;
        _outside = isOutsideFrustum() && worldBound != null;
        
        // slow evvvverything down by the animation speed
        time *= _animSpeed;
        if (_anim != null) {
            updateAnimation(time);
        }
        
        // update controllers and children with accumulated time
        _accum += time;
        if (_outside) {
            if (!wasOutside) {
                updateModelBound();
            }
            updateWorldVectors();
            worldBound = _modelBound.transform(getWorldRotation(),
                getWorldTranslation(), getWorldScale(), worldBound);
            
        } else {
            super.updateGeometricState(_accum, initiator);
            _accum = 0f;
        }
    }
    
    @Override // documentation inherited
    public void onDraw (Renderer r)
    {
        // if we switch from invisible to visible, we have to do a last-minute
        // full update (which only works if our meshes are enqueued)
        super.onDraw(r);
        if (_outside && !isOutsideFrustum()) {
            updateWorldData(0f);
        }
    }
    
    /**
     * Determines whether this node was determined to be entirely outside the
     * view frustum.
     */
    protected boolean isOutsideFrustum ()
    {
        for (Node node = this; node != null; node = node.getParent()) {
            if (node.getLastFrustumIntersection() == Camera.OUTSIDE_FRUSTUM) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Initializes the per-instance state of this model.
     */
    protected void initInstance ()
    {
        // initialize the controllers
        for (Object ctrl : getControllers()) {
            if (ctrl instanceof ModelController) {
                ((ModelController)ctrl).init(this);
            }
        }
    }
    
    /**
     * Updates the model's state according to the current animation.
     */
    protected void updateAnimation (float time)
    {
        // advance the frame counter if necessary
        while (_elapsed > 1f) {
            advanceFrameCounter();
            _elapsed -= 1f;
        }
        
        // update the target transforms if not outside the view frustum
        if (!_outside) {
            Spatial[] targets = _anim.transformTargets;
            Transform[] xforms = _anim.transforms[_fidx],
                nxforms = _anim.transforms[_nidx];
            for (int ii = 0; ii < targets.length; ii++) {
                xforms[ii].blend(nxforms[ii], _elapsed, targets[ii]);
            }
        }
        
        // if the next index is the same as this one, we are finished
        if (_fidx == _nidx) {
            _anim = null;
            _animObservers.apply(new AnimCompletedOp(_animName));
            return;
        }
        
        _elapsed += (time * _anim.frameRate);
    }
    
    /**
     * Advances the frame counter by one frame.
     */
    protected void advanceFrameCounter ()
    {
        _fidx = _nidx;
        int nframes = _anim.transforms.length;
        if (_anim.repeatType == Controller.RT_CLAMP) {
            _nidx = Math.min(_nidx + 1, nframes - 1);
            
        } else if (_anim.repeatType == Controller.RT_WRAP) {
            _nidx = (_nidx + 1) % nframes;
            
        } else { // _anim.repeatType == Controller.RT_CYCLE
            if ((_nidx + _fdir) < 0 || (_nidx + _fdir) >= nframes) {
                _fdir *= -1; // reverse direction
            }
            _nidx += _fdir;
        }
    }
    
    /**
     * Sets the model bound based on the current world bound.
     */
    protected void updateModelBound ()
    {
        if (worldBound == null) {
            return;
        }
        setTransform(getWorldTranslation(), getWorldRotation(),
            getWorldScale(), _xform);
        _xform.invertLocal();
        _xform.toTranslationVector(_trans);
        extractScale(_xform, _scale);
        _xform.toRotationQuat(_rot);
        _modelBound = worldBound.transform(_rot, _trans, _scale, _modelBound);
    }
    
    /**
     * Extracts the scale factor from the given transform and normalizes it.
     */
    protected static void extractScale (Matrix4f m, Vector3f scale)
    {
        scale.x = FastMath.sqrt(m.m00*m.m00 + m.m01*m.m01 + m.m02*m.m02);
        m.m00 /= scale.x;
        m.m01 /= scale.x;
        m.m02 /= scale.x;
        scale.y = FastMath.sqrt(m.m10*m.m10 + m.m11*m.m11 + m.m12*m.m12);
        m.m10 /= scale.y;
        m.m11 /= scale.y;
        m.m12 /= scale.y;
        scale.z = FastMath.sqrt(m.m20*m.m20 + m.m21*m.m21 + m.m22*m.m22);
        m.m20 /= scale.z;
        m.m21 /= scale.z;
        m.m22 /= scale.z;
    }
    
    /** A reference to the prototype, or <code>null</code> if this is a
     * prototype. */
    protected Model _prototype;
    
    /** For prototype models, a customized clone creator used to generate
     * instances. */
    protected ModelCloneCreator _ccreator;
    
    /** For instances, maps prototype nodes to their corresponding instance
     * nodes. */
    protected HashMap _pnodes;
    
    /** The model properties. */
    protected Properties _props;
    
    /** The model animations. */
    protected HashMap<String, Animation> _anims;
    
    /** The currently running animation, or <code>null</code> for none. */
    protected Animation _anim;
    
    /** The name of the currently running animation, if any. */
    protected String _animName;
    
    /** The current animation speed multiplier. */
    protected float _animSpeed = 1f;
    
    /** The index of the current and next frames. */
    protected int _fidx, _nidx;
    
    /** The direction for wrapping animations (+1 forward, -1 backward). */
    protected int _fdir;
    
    /** The frame portion elapsed since the start of the current frame. */
    protected float _elapsed;
    
    /** The amount of update time accumulated while outside of view frustum. */
    protected float _accum;
    
    /** The child node that contains the model's emissions in world space. */
    protected Node _emissionNode;
    
    /** The model space bounding volume. */
    protected BoundingVolume _modelBound;
    
    /** Whether or not we were outside the frustum at the last update. */
    protected boolean _outside;
    
    /** Temporary transform variables. */
    protected Matrix4f _xform = new Matrix4f();
    protected Vector3f _trans = new Vector3f(), _scale = new Vector3f();
    protected Quaternion _rot = new Quaternion();
    
    /** Animation completion listeners. */
    protected ObserverList<AnimationObserver> _animObservers =
        new ObserverList<AnimationObserver>(ObserverList.FAST_UNSAFE_NOTIFY);
    
    /** Used to notify observers of animation initiation. */
    protected class AnimStartedOp
        implements ObserverList.ObserverOp<AnimationObserver>
    {
        public AnimStartedOp (String name)
        {
            _name = name;
        }
        
        // documentation inherited from interface ObserverOp
        public boolean apply (AnimationObserver obs)
        {
            return obs.animationStarted(Model.this, _name);
        }
        
        /** The name of the animation started. */
        protected String _name;
    }
    
    /** Used to notify observers of animation completion. */
    protected class AnimCompletedOp
        implements ObserverList.ObserverOp<AnimationObserver>
    {
        public AnimCompletedOp (String name)
        {
            _name = name;
        }
        
        // documentation inherited from interface ObserverOp
        public boolean apply (AnimationObserver obs)
        {
            return obs.animationCompleted(Model.this, _name);
        }
        
        /** The name of the animation completed. */
        protected String _name;
    }
    
    /** Used to notify observers of animation cancellation. */
    protected class AnimCancelledOp
        implements ObserverList.ObserverOp<AnimationObserver>
    {
        public AnimCancelledOp (String name)
        {
            _name = name;
        }
        
        // documentation inherited from interface ObserverOp
        public boolean apply (AnimationObserver obs)
        {
            return obs.animationCancelled(Model.this, _name);
        }
        
        /** The name of the animation cancelled. */
        protected String _name;
    }
    
    /** Customized clone creator for models. */
    protected static class ModelCloneCreator extends CloneCreator
    {
        /** The model variant desired, or <code>null</code> for the default. */
        public String variant;
        
        /** A shared seed used to select textures consistently. */
        public int random;
        
        public ModelCloneCreator (Model toCopy)
        {
            super(toCopy);
            addProperty("vertices");
            addProperty("colors");
            addProperty("normals");
            addProperty("texcoords");
            addProperty("vboinfo");
            addProperty("indices");
            addProperty("obbtree");
            addProperty("displaylistid");
            addProperty("bound");
        }
        
        @Override // documentation inherited
        public void addProperty (String name)
        {
            props.put(name, Boolean.TRUE);
        }
        
        @Override // documentation inherited
        public void removeProperty (String name)
        {
            props.remove(name);
        }
        
        @Override // documentation inherited
        public Spatial createCopy ()
        {
            random = RandomUtil.getInt(Integer.MAX_VALUE);
            return super.createCopy();
        }
    }
    
    private static final long serialVersionUID = 1;
}