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

package org.openpnp.machine.reference;

import java.util.ArrayList;

import javax.swing.Action;

import org.openpnp.gui.support.PropertySheetWizardAdapter;
import org.openpnp.gui.support.Wizard;
import org.openpnp.machine.reference.psh.ActuatorsPropertySheetHolder;
import org.openpnp.machine.reference.psh.CamerasPropertySheetHolder;
import org.openpnp.machine.reference.psh.NozzlesPropertySheetHolder;
import org.openpnp.machine.reference.wizards.ReferenceHeadConfigurationWizard;
import org.openpnp.model.Configuration;
import org.openpnp.model.Location;
import org.openpnp.model.MappedAxes;
import org.openpnp.model.Part;
import org.openpnp.spi.Driver;
import org.openpnp.spi.HeadMountable;
import org.openpnp.spi.PropertySheetHolder;
import org.openpnp.spi.base.AbstractHead;
import org.openpnp.spi.base.AbstractHeadMountable;
import org.pmw.tinylog.Logger;

public class ReferenceHead extends AbstractHead {

    @Override
    public void home() throws Exception {
        Logger.debug("{}.home()", getName());
        HeadMountable hm = getHomingHeadMountable();
        MappedAxes mappedAxes = hm.getMappedAxes();
        
        for (Driver driver : mappedAxes.getMappedDrivers()) {
            ((ReferenceDriver) driver).home(this, mappedAxes, mappedAxes.getHomeLocation());
        }
        if (isVisualHomingEnabled()) {
            /*
             * The head camera should now be (if everything has homed correctly) directly
             * above the homing pin in the machine bed, use the head camera scan for this and make sure
             * this is exactly central - otherwise we move the camera until it is, and then reset all
             * the axis back to the fiducial location as this is calibrated home.
             */
            Part homePart = Configuration.get().getPart("FIDUCIAL-HOME");
            if (homePart != null) {
                Location homingLocation = Configuration.get().getMachine().getFiducialLocator()
                        .getHomeFiducialLocation(getHomingFiducialLocation(), homePart);

                if (homingLocation == null) {
                    // Homing failed
                    throw new Exception("Visual homing failed");
                }
                
                // Convert to raw coordinates;  
                homingLocation = hm.toRaw(homingLocation);
                
                // Reset the homing fiducial location as the new current location.
                for (Driver driver : mappedAxes.getMappedDrivers()) {
                    ((ReferenceDriver) driver).resetLocation(this, mappedAxes, getHomingFiducialLocation());
                }
            }
        }

        super.home();
        getMachine().fireMachineHeadActivity(this);
    }

    @Override
    public ReferenceHeadMountable getHomingHeadMountable() throws Exception {
        ReferenceHeadMountable hm = (ReferenceHeadMountable) getDefaultCamera();
        if (hm == null) {
            throw new Exception("Default Camera missing, cannot determine homing axes.");
        }
        return hm;
    }

    @Override
    public Wizard getConfigurationWizard() {
        return new ReferenceHeadConfigurationWizard(this);
    }

    @Override
    public String getPropertySheetHolderTitle() {
        return getClass().getSimpleName() + " " + getName();
    }

    @Override
    public PropertySheetHolder[] getChildPropertySheetHolders() {
        ArrayList<PropertySheetHolder> children = new ArrayList<>();
        children.add(new NozzlesPropertySheetHolder(this, "Nozzles", getNozzles(), null));
        children.add(new CamerasPropertySheetHolder(this, "Cameras", getCameras(), null));
        children.add(new ActuatorsPropertySheetHolder(this, "Actuators", getActuators(), null));
        return children.toArray(new PropertySheetHolder[] {});
    }

    @Override
    public PropertySheet[] getPropertySheets() {
        return new PropertySheet[] {new PropertySheetWizardAdapter(getConfigurationWizard())};
    }

    @Override
    public Action[] getPropertySheetHolderActions() {
        return null;
    }

    @Override
    public void moveToSafeZ(double speed) throws Exception {
        Logger.debug("{}.moveToSafeZ({})", getName(), speed);
        super.moveToSafeZ(speed);
    }

    @Override 
    public boolean isInsideSoftLimits(HeadMountable hm, Location location)  throws Exception {
        if (isSoftLimitsEnabled()) {
            /**
             * Since minLocation and maxLocation are captured with the Camera's coordinates, we need
             * to know where the Camera will land, not the HeadMountable.
             */
            if (hm instanceof ReferenceHeadMountable) {
                Location cameraLocation = ((AbstractHeadMountable) hm).toHeadLocation(location);
                cameraLocation = ((ReferenceCamera) getDefaultCamera()).fromHeadLocation(cameraLocation);
                Location minLocation = this.minLocation.convertToUnits(cameraLocation.getUnits());
                Location maxLocation = this.maxLocation.convertToUnits(cameraLocation.getUnits());
                if (cameraLocation.getX() < minLocation.getX() || cameraLocation.getX() > maxLocation.getX() ||
                        cameraLocation.getY() < minLocation.getY() || cameraLocation.getY() > maxLocation.getY()) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public void moveTo(HeadMountable hm, Location location, double speed) throws Exception {
        MappedAxes mappedAxes = hm.getMappedAxes();
        if (!mappedAxes.isEmpty()) {
            if (! isInsideSoftLimits(hm, location)) {
                throw new Exception(String.format("Can't move %s to %s, outside of soft limits on head %s.",
                        hm.getName(), location, getName()));
            }
            location = hm.toRaw(location);
            for (Driver driver : mappedAxes.getMappedDrivers()) {
                ((ReferenceDriver) driver).moveTo((ReferenceHeadMountable) hm, mappedAxes, location, speed);
            }
            getMachine().fireMachineHeadActivity(this);
        }
    }

    @Override
    public String toString() {
        return getName();
    }
    
    ReferenceDriver getDriver() {
        return getMachine().getDriver();
    }
    
    public ReferenceMachine getMachine() {
        return (ReferenceMachine) Configuration.get().getMachine();
    }
}
