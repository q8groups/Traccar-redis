/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.traccar.model;

/**
 *
 * @author q80
 */
public class RealTimePosition {
  private  String Device;
  private  String Company;
   private String TimeStamp;
  private String Lat;
  private  String Lon;

    /**
     * @return the Device
     */
    public String getDevice() {
        return Device;
    }

    /**
     * @param Device the Device to set
     */
    public void setDevice(String Device) {
        this.Device = Device;
    }

    /**
     * @return the Company
     */
    public String getCompany() {
        return Company;
    }

    /**
     * @param Company the Company to set
     */
    public void setCompany(String Company) {
        this.Company = Company;
    }

    /**
     * @return the TimeStamp
     */
    public String getTimeStamp() {
        return TimeStamp;
    }

    /**
     * @param TimeStamp the TimeStamp to set
     */
    public void setTimeStamp(String TimeStamp) {
        this.TimeStamp = TimeStamp;
    }

    /**
     * @return the Lat
     */
    public String getLat() {
        return Lat;
    }

    /**
     * @param Lat the Lat to set
     */
    public void setLat(String Lat) {
        this.Lat = Lat;
    }

    /**
     * @return the Lon
     */
    public String getLon() {
        return Lon;
    }

    /**
     * @param Lon the Lon to set
     */
    public void setLon(String Lon) {
        this.Lon = Lon;
    }
    
}
