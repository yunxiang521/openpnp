/*
 * Copyright (C) 2011 Jason von Nieda <jason@vonnieda.org>
 * 
 * This file is part of OpenPnP.
 * 
 * OpenPnP is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * OpenPnP is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with OpenPnP. If not, see
 * <http://www.gnu.org/licenses/>.
 * 
 * For more information about OpenPnP visit http://openpnp.org
 */

package org.openpnp.machine.reference.camera;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeSupport;
import java.net.URL;

import javax.imageio.ImageIO;

import org.openpnp.CameraListener;
import org.openpnp.gui.support.Wizard;
import org.openpnp.machine.reference.ReferenceCamera;
import org.openpnp.machine.reference.ReferenceMachine;
import org.openpnp.machine.reference.SimulationModeMachine;
import org.openpnp.machine.reference.camera.wizards.ImageCameraConfigurationWizard;
import org.openpnp.machine.reference.driver.NullDriver;
import org.openpnp.model.Configuration;
import org.openpnp.model.LengthUnit;
import org.openpnp.model.Location;
import org.openpnp.spi.PropertySheetHolder;
import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.core.Commit;

public class ImageCamera extends ReferenceCamera implements Runnable {
    private PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    @Attribute(required = false)
    private int fps = 24;

    @Element
    private String sourceUri = "classpath://samples/pnp-test/pnp-test.png";

    @Attribute(required = false)
    private int width = 640;

    @Attribute(required = false)
    private int height = 480;

    private BufferedImage source;

    private Thread thread;

    public ImageCamera() {
        setUnitsPerPixel(new Location(LengthUnit.Millimeters, 0.04233, 0.04233, 0, 0));
        try {
            setSourceUri(sourceUri);
        }
        catch (Exception e) {
            
        }
    }

    @SuppressWarnings("unused")
    @Commit
    protected void commit() throws Exception {
        super.commit();
        setSourceUri(sourceUri);
    }

    @Override
    public synchronized void startContinuousCapture(CameraListener listener) {
        start();
        super.startContinuousCapture(listener);
    }

    @Override
    public synchronized void stopContinuousCapture(CameraListener listener) {
        super.stopContinuousCapture(listener);
        if (listeners.size() == 0) {
            stop();
        }
    }

    private synchronized void stop() {
        if (thread != null && thread.isAlive()) {
            thread.interrupt();
            try {
                thread.join(3000);
            }
            catch (Exception e) {

            }
            thread = null;
        }
    }

    private synchronized void start() {
        if (thread == null) {
            thread = new Thread(this);
            thread.setDaemon(true);
            thread.start();
        }
    }

    public String getSourceUri() {
        return sourceUri;
    }

    public void setSourceUri(String sourceUri) throws Exception {
        String oldValue = this.sourceUri;
        this.sourceUri = sourceUri;
        pcs.firePropertyChange("sourceUri", oldValue, sourceUri);
        initialize();
    }

    @Override
    public synchronized BufferedImage internalCapture() {
        /*
         * Create a buffer that we will render the center tile and it's surrounding tiles to.
         */
        BufferedImage frame = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        Graphics2D gFrame = frame.createGraphics();
        
        Location location = SimulationModeMachine.getSimulatedPhysicalLocation(this, getLooking());
        
        double locationX = location.getX();
        double locationY = location.getY();

        double pixelX = locationX / getUnitsPerPixel().getX();
        double pixelY = locationY / getUnitsPerPixel().getY();

        int dx = (int) (pixelX - (width / 2));
        int dy = (int) (source.getHeight() - (pixelY + (height / 2)));
        int dx1 = dx;
        int dy1 = dy;
        int w1 = width;
        int h1 = height;

        if (dx < 0 || dy < 0 || dx+w1 > source.getWidth() || dy+h1 > source.getHeight()) {
            // crop to source area
            w1 += Math.min(0, dx);
            h1 += Math.min(0, dy);
            dx1 = Math.max(0, dx);
            dy1 = Math.max(0, dy);
            w1 = Math.min(w1, source.getWidth() - dx1);
            h1 = Math.min(h1, source.getHeight() - dy1);
            // paint the rest black
            gFrame.setColor(Color.black);
            gFrame.fillRect(0, 0, width, height);
        }
        gFrame.drawImage(source, dx1-dx, dy1-dy, dx1-dx+w1 - 1, dy1-dy+h1 - 1, dx1, dy1, dx1 + w1 - 1, dy1 + h1 - 1, null);
        
        SimulationModeMachine.drawSimulatedCameraNoise(gFrame, width, height);

        gFrame.dispose();

        return frame;
    }


    private synchronized void initialize() throws Exception {
        stop();

        if (sourceUri.startsWith("classpath://")) {
            source = ImageIO.read(getClass().getClassLoader()
                    .getResourceAsStream(sourceUri.substring("classpath://".length())));
        }
        else {
            source = ImageIO.read(new URL(sourceUri));
        }

        if (listeners.size() > 0) {
            start();
        }
    }


    public void run() {
        while (!Thread.interrupted()) {
            broadcastCapture(captureForPreview());
            try {
                Thread.sleep(100 / fps);
            }
            catch (InterruptedException e) {
                return;
            }
        }
    }

    @Override
    public Wizard getConfigurationWizard() {
        return new ImageCameraConfigurationWizard(this);
    }

    @Override
    public String getPropertySheetHolderTitle() {
        return getClass().getSimpleName() + " " + getName();
    }

    @Override
    public PropertySheetHolder[] getChildPropertySheetHolders() {
        return null;
    }
}
