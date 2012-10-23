/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011, 2012, FrostWire(TM). All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.frostwire.gui.upnp.desktop;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.teleal.cling.UpnpService;
import org.teleal.cling.UpnpServiceImpl;
import org.teleal.cling.model.ValidationException;
import org.teleal.cling.model.meta.Device;
import org.teleal.cling.model.meta.LocalDevice;

import com.frostwire.gui.upnp.UPnPFWDevice;
import com.frostwire.gui.upnp.UPnPManager;
import com.frostwire.gui.upnp.UPnPRegistryListener;

/**
 * 
 * @author gubatron
 * @author aldenml
 * 
 */
public class UPnPService implements Runnable {

    private static final Logger LOG = Logger.getLogger(UPnPService.class.getName());

    private UpnpService service;
    private UPnPRegistryListener registryListener;

    private static LocalDevice localDevice;

    public UPnPService(UPnPRegistryListener registryListener) {
        this.registryListener = registryListener;
    }

    public UpnpService getService() {
        return service;
    }

    public static LocalDevice getLocalDevice() {
        return localDevice;
    }

    public void start() {
        if (service == null) {
            Thread t = new Thread(this);
            t.setDaemon(false);
            t.start();
        }
    }

    public void run() {
        try {

            service = new UpnpServiceImpl();

            Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    service.shutdown();
                }
            });

            if (localDevice == null) {
                try {
                    localDevice = createLocalDevice();
                    this.service.getRegistry().addDevice(localDevice);
                } catch (ValidationException e) {
                    LOG.log(Level.WARNING, "Unable to create and register local UPnP frostwire device", e);
                }
            }

            // refresh the list with all known devices
            for (Device<?, ?, ?> device : this.service.getRegistry().getDevices()) {
                registryListener.deviceAdded(device);
            }

            // getting ready for future device advertisements
            this.service.getRegistry().addListener(registryListener);

            // search asynchronously for all devices
            this.service.getControlPoint().search();
            
        } catch (Throwable e) {
            LOG.log(Level.WARNING, "Exception occured with the UPnP framework", e);
        }
    }

    private LocalDevice createLocalDevice() throws ValidationException {
        UPnPFWDevice device = UPnPManager.instance().getUPnPLocalDevice();

        return new LocalDevice(device.getIdentity(), device.getType(), device.getDetails(), device.getIcon(), device.getServices());
    }
}