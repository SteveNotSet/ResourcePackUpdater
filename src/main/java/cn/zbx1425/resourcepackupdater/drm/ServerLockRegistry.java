package cn.zbx1425.resourcepackupdater.drm;

import cn.zbx1425.resourcepackupdater.ResourcePackUpdater;
import cn.zbx1425.resourcepackupdater.mappings.Text;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class ServerLockRegistry {

    public static boolean lockAllSyncedPacks = true;

    private static String localServerLock;

    private static String remoteServerLock;
    private static String packAppliedServerLock;

    private static boolean serverLockPrefetched = false;

    public static void updateLocalServerLock(File rpFolder) {
        if (lockAllSyncedPacks) {
            localServerLock = null; // So that when no longer lockAllSyncedPacks, the pack will reload
            return;
        }
        try {
            JsonObject metaObj = ResourcePackUpdater.JSON_PARSER.parse(IOUtils.toString(
                    AssetEncryption.wrapInputStream(new FileInputStream(rpFolder.toPath().resolve("pack.mcmeta").toFile()))
                    , StandardCharsets.UTF_8)).getAsJsonObject();
            if (metaObj.has("zbx_rpu_server_lock")) {
                localServerLock = metaObj.get("zbx_rpu_server_lock").getAsString();
                if (!serverLockPrefetched) {
                    remoteServerLock = localServerLock;
                    packAppliedServerLock = remoteServerLock;
                    ResourcePackUpdater.LOGGER.info("Server lock info prefetched from local pack.");
                    serverLockPrefetched = true;
                }
            } else {
                localServerLock = null;
            }
        } catch (Exception ignored) {
            localServerLock = null;
        }
    }

    public static boolean shouldRefuseProvidingFile(String resourcePath) {
        // For Star River City, force enable packs regardless of incompletion or any locks
        /*
        if (Objects.equals(resourcePath, "pack.mcmeta") || Objects.equals(resourcePath, "pack.png")) return false;
        if (lockAllSyncedPacks) return true;
        if (localServerLock == null) return false;
        return !Objects.equals(localServerLock, remoteServerLock);*/
        return false;
    }

    public static void onLoginInitiated() {
        remoteServerLock = null;
    }

    public static void onSetServerLock(String serverLock) {
        remoteServerLock = serverLock;
    }

    public static void onAfterSetServerLock() {
        if (lockAllSyncedPacks) {
            Minecraft.getInstance().getToasts().addToast(new SystemToast(SystemToast.SystemToastIds.PACK_LOAD_FAILURE,
                    Text.literal("同步资源包不完整"), Text.literal("您可按 F3+T 重试下载。如有错误请联络管理人员。")
            ));
        }

        if (localServerLock == null) {
            ResourcePackUpdater.LOGGER.info("Asset coordination not required by this pack.");
        } else if (remoteServerLock == null) {
            ResourcePackUpdater.LOGGER.info("Asset coordination identifier not received.");
        } else if (!remoteServerLock.equals(localServerLock)) {
            ResourcePackUpdater.LOGGER.info("Asset coordination identifier differs.");
        } else if (lockAllSyncedPacks) {
            ResourcePackUpdater.LOGGER.info("Asset coordination is unavailable due to incomplete synchronization.");
        } else {
            ResourcePackUpdater.LOGGER.info("Asset coordination is applicable.");
        }
        if (localServerLock != null && !Objects.equals(packAppliedServerLock, remoteServerLock)) {
            packAppliedServerLock = remoteServerLock;
            Minecraft.getInstance().execute(() -> Minecraft.getInstance().reloadResourcePacks());
        }
    }
}
