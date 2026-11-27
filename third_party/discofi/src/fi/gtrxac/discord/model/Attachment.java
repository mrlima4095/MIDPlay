package fi.gtrxac.discord.model;

import cc.nnproject.json.JSONObject;
import fi.gtrxac.bluewap.*;

/**
 * Created by Leap on 4.10.2026.
 */

public class Attachment {
    private static final String[] VIDEO_EXTENSIONS = {
        ".mp4", ".3gp", ".webm", ".mov", ".m4v", ".mkv", ".avi", ".gifv"
    };

    public String id;
    public String filename;
    public String contentType;
    public int size;

    public int width;
    public int height;

    public String url;
    public String proxyUrl;

    // images and videos can be previewed (videos as their first frame)
    public boolean previewable;
    public boolean isVideo;

    public Attachment(JSONObject data) {
        this(data, false);
    }

    public Attachment(JSONObject data, boolean isEmbed) {
        width = data.getInt("width", 0);
        height = data.getInt("height", 0);
        contentType = data.getString("content_type", null);

        url = data.getString("url", null);
        proxyUrl = data.getString("proxy_url", null);

        if (!isEmbed) {
            id = data.getString("id", null);
            filename = data.getString("filename", "attachment");
            size = data.getInt("size", 0);
        }

        isVideo = (isContentType("video/"))
                || (filename != null && endsWithAny(filename.toLowerCase(), VIDEO_EXTENSIONS));

        // Anything with dimensions can be rendered by the media proxy
        previewable = (proxyUrl != null && width > 0);
    }

    private boolean isContentType(String check) {
        return contentType != null && contentType.startsWith(check);
    }

    /**
     * Get the URL that can be used to download this attachment.
     * 
     * Use cdnProxyUrl = null to connect directly.
     */
    public String getDownloadUrl(String cdnProxyUrl) {
        if (cdnProxyUrl == null) return url;

        String route = url.substring("https://cdn.discordapp.com".length());

        return cdnProxyUrl + route;
    }

    /**
     * Get the media proxy URL that can be used to download a preview 
     * for this attachment at the specified resolution.
     * 
     * Discord's media proxy can be further proxied if they ever start requiring HTTPS
     * or if Discord is blocked in the user's region.
     * 
     * Use mediaProxyUrl = null to use Discord's media proxy without another proxy.
     */
    public String getPreviewUrl(String mediaProxyUrl, int maxWidth, int maxHeight) {
        String upstreamUrl = getUpstreamPreviewUrl(maxWidth, maxHeight);
        if (mediaProxyUrl == null) return upstreamUrl;

        String route = upstreamUrl.substring("https://media.discordapp.net".length());

        return mediaProxyUrl + route;
    }

    private String getUpstreamPreviewUrl(int maxWidth, int maxHeight) {
        if (!previewable) return null;

        int[] actualSize = Util.resizeFit(width, height, maxWidth, maxHeight);

        return appendQuery(proxyUrl, 
            "format=jpeg&quality=low&width=" + actualSize[0] + "&height=" + actualSize[1]);
    }

    private static String appendQuery(String url, String query) {
        if (url == null) return null;

        // Discord's proxy URLs already have a '&' at the end
        if (!url.endsWith("?") && !url.endsWith("&")) {
            url += (url.indexOf('?') == -1) ? "?" : "&";
        }

        return url + query;
    }

    private static boolean endsWithAny(String name, String[] extensions) {
        for (int i = 0; i < extensions.length; i++) {
            if (name.endsWith(extensions[i])) return true;
        }
        return false;
    }

    public static String formatSize(int size) {
        if (size >= 1000000) return (size/1000000) + " MB";
        if (size >= 1000) return (size/1000) + " kB";
        return size + " bytes";
    }
}
