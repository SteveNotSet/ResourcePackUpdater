package cn.zbx1425.resourcepackupdater.io;

import cn.zbx1425.resourcepackupdater.Config;
import cn.zbx1425.resourcepackupdater.ResourcePackUpdater;
import cn.zbx1425.resourcepackupdater.gui.gl.GlHelper;
import cn.zbx1425.resourcepackupdater.gui.GlProgressScreen;
import cn.zbx1425.resourcepackupdater.io.network.DownloadDispatcher;
import cn.zbx1425.resourcepackupdater.io.network.DownloadTask;
import cn.zbx1425.resourcepackupdater.io.network.PackOutputStream;
import cn.zbx1425.resourcepackupdater.io.network.RemoteMetadata;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class Dispatcher {

    private RemoteMetadata remoteMetadata;
    private LocalMetadata localMetadata;

    public boolean runSync(String baseDir, Config.SourceProperty source, ProgressReceiver cb) throws Exception {
        try {
            if (source.baseUrl.isEmpty()) {
                throw new IOException("未配置下载源。请将配置文件放到 config 目录！");
            }

            cb.printLog("资源同步实用程序喵（星海专版喵） v" + ResourcePackUpdater.MOD_VERSION + " © Zbx1425, www.zbx1425.cn");
            cb.printLog("这是资源地址喵：" + source.baseUrl);
            cb.printLog("这是目标目录喵：" + baseDir);
            cb.printLog("");

            localMetadata = new LocalMetadata(baseDir);
            remoteMetadata = new RemoteMetadata(source.baseUrl);

            byte[] remoteChecksum = null;

            if (source.hasDirHash) {
                cb.printLog("正在获取远程目录校验码喵……");
                remoteChecksum = remoteMetadata.fetchDirChecksum(cb);
                cb.amendLastLog("完成喵");
                cb.printLog("远程目录校验码是 " + Hex.encodeHexString(remoteChecksum) + "喵。");
            } else {
                cb.printLog("这个服务器没有提供远程目录校验码喵。");
                cb.printLog("正在获取文件清单喵……");
                remoteMetadata.fetch(cb);
                cb.amendLastLog("完成喵");
                cb.setProgress(0, 0);
            }
            // Now, either checksum or full metadata is fetched, with the encryption switch.

            cb.printLog("正在扫描本地文件喵……");
            localMetadata.scanDir(remoteMetadata.encrypt, cb);
            cb.amendLastLog("完成喵");
            byte[] localChecksum = localMetadata.getDirChecksum();
            cb.printLog("本地目录校验码是 " + Hex.encodeHexString(localChecksum) + "喵。");

            if (localMetadata.files.size() < 1) {
                cb.printLog("正在下载来自远程服务器的资源包喵。");
                cb.printLog("这是第一次下载喵。坐下来喝杯茶等一会喵。");
            }
            if (remoteChecksum != null) {
                if (Arrays.equals(localChecksum, remoteChecksum)) {
                    cb.printLog("所有文件均已为最新了喵。");
                    cb.setProgress(1, 1);
                    cb.printLog("");
                    cb.printLog("完成了喵！谢谢喵。（= > _ < =)");
                    return true;
                } else {
                    // We haven't fetched the full metadata yet, do it now.
                    cb.printLog("正在获取文件清单喵……");
                    remoteMetadata.fetch(cb);
                    cb.amendLastLog("完成喵");
                    cb.setProgress(0, 0);
                }
            }

            List<String> dirsToCreate = localMetadata.getDirsToCreate(remoteMetadata);
            List<String> dirsToDelete = localMetadata.getDirsToDelete(remoteMetadata);
            List<String> filesToCreate = localMetadata.getFilesToCreate(remoteMetadata);
            List<String> filesToUpdate = localMetadata.getFilesToUpdate(remoteMetadata);
            List<String> filesToDelete = localMetadata.getFilesToDelete(remoteMetadata);
            cb.printLog(String.format("找到了 %d 个新目录喵，和 %d 个要删除的目录喵。",
                    dirsToCreate.size(), dirsToDelete.size()));
            cb.printLog(String.format("找到了 %d 个新文件、%d 个要修改的文件、%d 个要删除的文件喵。",
                    filesToCreate.size(), filesToUpdate.size(), filesToDelete.size()));

            cb.printLog("正在创建或删除需要的目录喵……");
            for (String dir : dirsToCreate) {
                Files.createDirectories(Paths.get(baseDir, dir));
            }
            for (String file : filesToDelete) {
                Files.deleteIfExists(Paths.get(baseDir, file));
            }
            for (String dir : dirsToDelete) {
                Path dirPath = Paths.get(baseDir, dir);
                if (Files.isDirectory(dirPath)) FileUtils.deleteDirectory(dirPath.toFile());
            }
            cb.amendLastLog("完成喵");

            remoteMetadata.beginDownloads(cb);
            cb.printLog("正在下载文件喵……");
            DownloadDispatcher downloadDispatcher = new DownloadDispatcher(cb);
            for (String file : Stream.concat(filesToCreate.stream(), filesToUpdate.stream()).toList()) {
                DownloadTask task = new DownloadTask(downloadDispatcher,
                        remoteMetadata.baseUrl + "/dist/" + file, file, remoteMetadata.files.get(file).size);
                downloadDispatcher.dispatch(task, () -> new PackOutputStream(Paths.get(baseDir, file),
                        remoteMetadata.encrypt, localMetadata.hashCache, remoteMetadata.files.get(file).hash));
            }
            while (!downloadDispatcher.tasksFinished()) {
                downloadDispatcher.updateSummary();
                ((GlProgressScreen)cb).redrawScreen(true);
                Thread.sleep(1000 / 30);
            }
            remoteMetadata.downloadedBytes += downloadDispatcher.downloadedBytes;
            downloadDispatcher.close();
            localMetadata.saveHashCache();

            cb.setInfo("", "");
            cb.setProgress(1, 1);
            cb.printLog("");
            remoteMetadata.endDownloads(cb);
            cb.printLog("完成了喵！谢谢喵。（= > _ < =)");
            return true;
        } catch (GlHelper.MinecraftStoppingException ex) {
            throw ex;
        } catch (Exception ex) {
            cb.setException(ex);
            return false;
        }
    }
}
