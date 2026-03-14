package cn.zbx1425.resourcepackupdater.util;

import cn.zbx1425.resourcepackupdater.ResourcePackUpdater;

public class MismatchingVersionException extends Exception {

    public MismatchingVersionException(String requested, String current) {
        super("\n\n" +
            String.format("资源同步实用程序版本不符：请您去下载安装 %s 版（您现有 %s）", requested, current) + "\n" +
            String.format("Please update your Resource Pack Updater mod to the version %s (You are now using %s)", requested, current) +
            "\n\n"
        );
    }

    public MismatchingVersionException(String message) {
        super(message);
    }
}
