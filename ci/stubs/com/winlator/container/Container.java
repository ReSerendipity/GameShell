package com.winlator.container;
import java.io.File;
public class Container {
    public static final String DEFAULT_ENV_VARS = "";
    public static final String DEFAULT_DRIVES = "";
    public static final String DEFAULT_WINCOMPONENTS = "direct3d=1,directsound=1,directmusic=1,directshow=0,directplay=0,xaudio=1,vcrun2005=0,vcrun2010=1,wmdecoder=1";
    public final int id = 0;
    public Container(int id) {}
    public File getRootDir() { return null; }
    public String getEnvVars() { return ""; }
    public void setEnvVars(String envVars) {}
    public void putExtra(String name, Object value) {}
    public String getExtra(String name) { return null; }
    public String getExtra(String name, String fallback) { return fallback; }
    public void saveData() {}
    public String getName() { return ""; }
    public String getScreenSize() { return ""; }
    public void setScreenSize(String screenSize) {}
    public String getGraphicsDriver() { return ""; }
    public String getDXWrapper() { return ""; }
    public void setDXWrapper(String dxwrapper) {}
    public String getDXWrapperConfig() { return ""; }
    public void setDXWrapperConfig(String dxwrapperConfig) {}
    public String getAudioDriver() { return ""; }
    public String getWinComponents() { return ""; }
    public void setWinComponents(String wincomponents) {}
    public String getBox64Preset() { return ""; }
    public void setBox64Preset(String box64Preset) {}
    public String getWineVersion() { return ""; }
    public byte getStartupSelection() { return 0; }
}
