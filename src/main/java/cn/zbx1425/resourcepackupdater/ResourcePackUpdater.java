package cn.zbx1425.resourcepackupdater;

import cn.zbx1425.resourcepackupdater.drm.ServerLockRegistry;
import cn.zbx1425.resourcepackupdater.gui.gl.GlHelper;
import cn.zbx1425.resourcepackupdater.gui.GlProgressScreen;
import cn.zbx1425.resourcepackupdater.io.Dispatcher;
import cn.zbx1425.resourcepackupdater.io.network.DummyTrustManager;
import cn.zbx1425.resourcepackupdater.mappings.Text;
import cn.zbx1425.resourcepackupdater.util.MismatchingVersionException;
import cn.zbx1425.resourcepackupdater.util.MtrVersion;
import cn.zbx1425.resourcepackupdater.util.RPUClientVersionSupplier;
import com.google.gson.JsonParser;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.repository.PackRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ResourcePackUpdater implements ModInitializer {

    public static final String MOD_ID = "resourcepackupdater";
    public static final Logger LOGGER = LogManager.getLogger("ResourcePackUpdater");
    public static String MOD_VERSION = "";

    public static final Config CONFIG = new Config();
    public static final JsonParser JSON_PARSER = new JsonParser();

    @Override
    public void onInitialize() {
        MOD_VERSION = FabricLoader.getInstance().getModContainer(MOD_ID).get()
                .getMetadata().getVersion().getFriendlyString();
        try {
            CONFIG.load();
        } catch (IOException e) {
            LOGGER.error("Failed to load config", e);
        }

        ServerConfigurationConnectionEvents.CONFIGURE.register((handler, server) -> {
            if (ServerConfigurationNetworking.canSend(handler, ServerLockS2CPacket.TYPE)) {
                handler.addTask(new ServerLockTask(CONFIG.serverLockKey.value));
            } else {
                if (ResourcePackUpdater.CONFIG.clientEnforceInstall.value) {
                    handler.disconnect(Text.literal(new MismatchingVersionException(ResourcePackUpdater.MOD_VERSION, "未安裝 NOT INSTALLED").getMessage().trim()));
                }
            }
        });
        ServerConfigurationNetworking.registerGlobalReceiver(ClientVersionC2SPacket.TYPE, (packet, handler, sender) -> {
            String clientVersion = packet.clientVersion;
            if (!ResourcePackUpdater.CONFIG.clientEnforceVersion.value.isEmpty()) {
                String versionCriteria = ResourcePackUpdater.CONFIG.clientEnforceVersion.value.replace("current", ResourcePackUpdater.MOD_VERSION);
                if (!MtrVersion.parse(clientVersion).matches(versionCriteria)) {
                    handler.disconnect(Text.literal(new MismatchingVersionException(ResourcePackUpdater.MOD_VERSION, clientVersion).getMessage().trim()));
                }
            }
            handler.completeTask(ServerLockTask.TYPE);
        });
    }
}
