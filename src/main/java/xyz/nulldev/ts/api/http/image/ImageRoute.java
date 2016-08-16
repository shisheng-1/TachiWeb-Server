package xyz.nulldev.ts.api.http.image;

import eu.kanade.tachiyomi.data.database.models.Chapter;
import eu.kanade.tachiyomi.data.database.models.Manga;
import eu.kanade.tachiyomi.data.download.DownloadManager;
import eu.kanade.tachiyomi.data.source.Source;
import eu.kanade.tachiyomi.data.source.model.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.utils.IOUtils;
import xyz.nulldev.ts.DIReplacement;
import xyz.nulldev.ts.library.Library;
import xyz.nulldev.ts.api.http.TachiWebRoute;
import xyz.nulldev.ts.util.ChapterUtils;
import xyz.nulldev.ts.util.LeniantParser;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * Project: TachiServer
 * Author: nulldev
 * Creation Date: 12/07/16
 */
public class ImageRoute extends TachiWebRoute {

    private static Logger logger = LoggerFactory.getLogger(ImageRoute.class);

    private DownloadManager downloadManager = DIReplacement.get().injectDownloadManager();

    public ImageRoute(Library library) {
        super(library);
    }

    @Override
    public Object handleReq(Request request, Response response) throws Exception {
        Long mangaId = LeniantParser.parseLong(request.params(":mangaId"));
        Long chapterId = LeniantParser.parseLong(request.params(":chapterId"));
        Integer page = LeniantParser.parseInteger(request.params(":page"));
        if (mangaId == null) {
            return error("MangaID must be specified!");
        } else if (chapterId == null) {
            return error("ChapterID must be specified!");
        }
        if (page == null || page < 0) {
            page = 0;
        }
        Manga manga = getLibrary().getManga(mangaId);
        if (manga == null) {
            return error("The specified manga does not exist!");
        }
        Source source;
        try {
            source = DIReplacement.get().injectSourceManager().get(manga.getSource());
            if (source == null) {
                throw new IllegalArgumentException();
            }
        } catch (Exception e) {
            return error("This manga's source is not loaded!");
        }
        Chapter chapter = getLibrary().getChapter(chapterId);
        if (chapter == null) {
            return error("The specified chapter does not exist!");
        }
        List<Page> pages = ChapterUtils.getPageList(downloadManager, source, manga, chapter);
        if (pages == null) {
            return error("Failed to fetch page list!");
        }
        Page pageObj = null;
        for (Page toCheck : pages) {
            if (toCheck.getPageNumber() == page) {
                pageObj = toCheck;
                break;
            }
        }
        if (pageObj == null) {
            return error("Could not find specified page!");
        }
        //Get downloaded image if downloaded
        if(downloadManager.isChapterDownloaded(source, manga, chapter)) {
            File downloadDir = downloadManager.getAbsoluteChapterDirectory(source, manga, chapter);
            pageObj = downloadManager.getDownloadedImage(pageObj, downloadDir).toBlocking().first();
        }
        //Download image if not downloaded
        if(pageObj.getStatus() != Page.READY) {
            pageObj = source.fetchImage(pageObj).toBlocking().first();
        }
        try(OutputStream outputStream = response.raw().getOutputStream()) {
            if (pageObj.getStatus() == Page.READY && pageObj.getImagePath() != null) {
                response.status(200);
                response.type(Files.probeContentType(Paths.get(pageObj.getImagePath())));
                IOUtils.copy(
                        new FileInputStream(pageObj.getImagePath()),
                        outputStream);
            } else {
                throw new IllegalStateException();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return error("Failed to download page!");
        }
        return "";
    }
}
