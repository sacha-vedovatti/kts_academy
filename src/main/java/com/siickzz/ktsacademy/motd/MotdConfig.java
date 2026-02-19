package com.siickzz.ktsacademy.motd;

import java.util.List;

public class MotdConfig {

    public List<LineConfig> lines;

    public static class LineConfig
    {
        public String text = "";
        public String font = "normal";
        public boolean bold = false;
        public GradientConfig gradient = null;
    }

    public static class GradientConfig
    {
        public String from = "#FFFFFF";
        public String to = "#FFFFFF";
    }
}
