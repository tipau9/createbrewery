package com.createbrewery.compat.etched;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import gg.moonflower.etched.api.util.DownloadProgressListener;
import gg.moonflower.etched.api.sound.source.AudioSource;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.util.GsonHelper;
import net.neoforged.fml.loading.FMLPaths;
import net.sourceforge.jaad.aac.Decoder;
import net.sourceforge.jaad.aac.SampleBuffer;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Resolves and decodes modern SoundCloud audio streams for the DJ booth and jukebox records.
 *
 * <p>SoundCloud has discontinued its legacy MP3 HLS endpoints ({@code audio/mpegurl}), causing
 * upstream Etched to fail with HTTP 404 when querying the media stream. Modern SoundCloud tracks
 * only provide AAC streams in fragmented MP4 (fMP4) HLS chunks ({@code audio/mp4}).
 *
 * <p>This resolver:
 * <ol>
 *   <li>Queries the track metadata and modern HLS AAC stream URL using a valid client ID.</li>
 *   <li>Checks a persistent on-disk cache ({@code .minecraft/etched_cache/soundcloud/<hash>.le.wav}).</li>
 *   <li>Downloads the HLS segment chunks and decodes the AAC frames to 16-bit 44.1 kHz Stereo PCM via JAAD.</li>
 *   <li>Writes a standard 44-byte RIFF WAV file and returns a {@link CachedWavAudioSource}.</li>
 * </ol>
 */
public final class SoundCloudAudioResolver {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String FALLBACK_CLIENT_ID = "V7f0p2q7JD4voO1Esqg6bwwtNYcYAtLC";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private SoundCloudAudioResolver() {}

    public static boolean isSoundCloudUrl(String url) {
        if (url == null || url.isBlank()) return false;
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.contains("soundcloud.com/") || lower.contains("snd.sc/");
    }

    public static CompletableFuture<AudioSource> getAudioSource(
            String trackUrl,
            DownloadProgressListener listener,
            Proxy proxy,
            AudioSource.AudioFileType type) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                if (listener != null) {
                    listener.progressStartRequest(Component.translatable("record.etched.resolvingTracks"));
                }

                Path cacheDir = FMLPaths.GAMEDIR.get().resolve("etched_cache").resolve("soundcloud");
                Files.createDirectories(cacheDir);

                String hash = hashUrl(trackUrl);
                // ".le": WAVs cached before the endianness fix hold byte-swapped PCM (white noise); never reuse them
                Path wavFile = cacheDir.resolve(hash + ".le.wav");

                if (Files.exists(wavFile) && Files.size(wavFile) > 44) {
                    LOGGER.info("Create Brewery: using cached SoundCloud WAV for {} ({})", trackUrl, wavFile.getFileName());
                    if (listener != null) listener.onSuccess();
                    return new CachedWavAudioSource(wavFile);
                }

                Path tempWav = cacheDir.resolve(hash + ".tmp");
                LOGGER.info("Create Brewery: downloading and decoding SoundCloud track: {}", trackUrl);

                downloadAndDecodeToWav(trackUrl, tempWav, listener, proxy);

                Files.move(tempWav, wavFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                LOGGER.info("Create Brewery: successfully cached SoundCloud WAV: {} ({} bytes)", wavFile.getFileName(), Files.size(wavFile));

                if (listener != null) listener.onSuccess();
                return new CachedWavAudioSource(wavFile);

            } catch (Throwable t) {
                LOGGER.error("Create Brewery: failed to resolve/decode SoundCloud track: {}", trackUrl, t);
                if (listener != null) listener.onFail();
                throw new CompletionException("Failed to decode SoundCloud track: " + trackUrl, t);
            }
        }, Util.nonCriticalIoPool());
    }

    private static void downloadAndDecodeToWav(
            String trackUrl,
            Path targetTempWav,
            DownloadProgressListener listener,
            Proxy proxy) throws Exception {

        String clientId = getActiveClientId(proxy);

        // 1. Resolve track metadata
        String resolveUrl = "https://api-v2.soundcloud.com/resolve?url="
                + URLEncoder.encode(trackUrl, StandardCharsets.UTF_8)
                + "&client_id=" + clientId;

        JsonObject trackJson = fetchJson(resolveUrl, proxy);
        String title = GsonHelper.getAsString(trackJson, "title", "Track");

        JsonObject media = GsonHelper.getAsJsonObject(trackJson, "media", null);
        if (media == null) {
            throw new IOException("Track media object missing in SoundCloud API response for: " + trackUrl);
        }

        JsonArray transcodings = GsonHelper.getAsJsonArray(media, "transcodings", new JsonArray());
        String hlsMediaUrl = null;

        // Prefer 160kbps AAC HLS, fallback to 96kbps AAC HLS
        for (JsonElement elem : transcodings) {
            if (!elem.isJsonObject()) continue;
            JsonObject tc = elem.getAsJsonObject();
            JsonObject format = GsonHelper.getAsJsonObject(tc, "format", null);
            if (format == null) continue;

            String protocol = GsonHelper.getAsString(format, "protocol", "");
            String mimeType = GsonHelper.getAsString(format, "mime_type", "");

            if ("hls".equalsIgnoreCase(protocol) && mimeType.toLowerCase(Locale.ROOT).contains("audio/mp4")) {
                hlsMediaUrl = GsonHelper.getAsString(tc, "url", null);
                if (mimeType.contains("160k") || hlsMediaUrl != null) {
                    break;
                }
            }
        }

        if (hlsMediaUrl == null) {
            throw new IOException("No playable AAC HLS stream found for SoundCloud track: " + trackUrl);
        }

        // 2. Fetch HLS playlist URL
        String streamAuthUrl = hlsMediaUrl + (hlsMediaUrl.contains("?") ? "&" : "?") + "client_id=" + clientId;
        JsonObject streamJson = fetchJson(streamAuthUrl, proxy);
        String playlistUrl = GsonHelper.getAsString(streamJson, "url", null);
        if (playlistUrl == null) {
            throw new IOException("Could not obtain HLS playlist URL from: " + streamAuthUrl);
        }

        // 3. Read M3U8 playlist
        List<String> segmentUrls = new ArrayList<>();
        String initUrl = null;

        HttpURLConnection plConn = openConnection(playlistUrl, proxy);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(plConn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.contains("init.mp4")) {
                    int u1 = line.indexOf("URI=\"");
                    if (u1 != -1) {
                        int u2 = line.indexOf("\"", u1 + 5);
                        if (u2 != -1) {
                            initUrl = line.substring(u1 + 5, u2);
                        }
                    }
                } else if (line.startsWith("http://") || line.startsWith("https://")) {
                    segmentUrls.add(line);
                }
            }
        }

        if (segmentUrls.isEmpty()) {
            throw new IOException("No audio segment URLs found in M3U8 playlist: " + playlistUrl);
        }

        if (listener != null) {
            listener.progressStartLoading();
        }

        // 4. Download init.mp4 and extract AudioSpecificConfig
        byte[] asc = new byte[]{0x12, 0x10}; // Standard 44.1 kHz Stereo AAC-LC fallback
        if (initUrl != null) {
            try {
                byte[] initBytes = downloadBytes(initUrl, proxy);
                byte[] parsedAsc = extractAudioSpecificConfig(initBytes);
                if (parsedAsc != null && parsedAsc.length >= 2) {
                    asc = parsedAsc;
                }
            } catch (Exception e) {
                LOGGER.warn("Create Brewery: could not parse AudioSpecificConfig from init.mp4, using standard AAC-LC", e);
            }
        }

        // 5. Decode segments directly to temporary WAV file
        Decoder decoder = new Decoder(asc);
        SampleBuffer sampleBuffer = new SampleBuffer();
        sampleBuffer.setBigEndian(false); // JAAD defaults to big-endian; RIFF WAV PCM is little-endian

        long totalPcmBytes = 0;
        int sampleRate = 44100;
        int channels = 2;

        try (RandomAccessFile raf = new RandomAccessFile(targetTempWav.toFile(), "rw")) {
            raf.setLength(0);
            // Reserve 44 bytes for RIFF WAV header
            raf.seek(44);

            int totalSegments = segmentUrls.size();
            for (int i = 0; i < totalSegments; i++) {
                String segUrl = segmentUrls.get(i);
                byte[] segBytes = downloadBytes(segUrl, proxy);

                List<Integer> frameSizes = parseTrunSizes(segBytes);
                int mdatOffset = findMdatOffset(segBytes);

                if (mdatOffset != -1 && !frameSizes.isEmpty()) {
                    int pos = mdatOffset;
                    for (int frameSize : frameSizes) {
                        if (pos + frameSize > segBytes.length) break;
                        byte[] frame = new byte[frameSize];
                        System.arraycopy(segBytes, pos, frame, 0, frameSize);
                        pos += frameSize;

                        try {
                            decoder.decodeFrame(frame, sampleBuffer);
                            byte[] pcm = sampleBuffer.getData();
                            if (pcm != null && pcm.length > 0) {
                                raf.write(pcm);
                                totalPcmBytes += pcm.length;
                                sampleRate = sampleBuffer.getSampleRate();
                                channels = sampleBuffer.getChannels();
                            }
                        } catch (Throwable frameError) {
                            // Drop single corrupted frame without failing the entire track
                        }
                    }
                }

                if (listener != null) {
                    listener.progressStage((float) (i + 1) / totalSegments);
                }
            }

            // 6. Write final RIFF WAV header at position 0
            raf.seek(0);
            byte[] header = createWavHeader((int) totalPcmBytes, sampleRate, channels);
            raf.write(header);
        }
    }

    private static String getActiveClientId(Proxy proxy) {
        try {
            Class<?> trackerClass = Class.forName("gg.moonflower.etched.common.sound.download.SoundCloudIdTracker");
            java.lang.reflect.Method fetch = trackerClass.getMethod("fetch", Proxy.class);
            String id = (String) fetch.invoke(null, proxy);
            if (id != null && !id.isBlank()) return id;
        } catch (Throwable ignored) {}
        return FALLBACK_CLIENT_ID;
    }

    private static JsonObject fetchJson(String urlStr, Proxy proxy) throws IOException {
        HttpURLConnection conn = openConnection(urlStr, proxy);
        int code = conn.getResponseCode();
        if (code != 200) {
            throw new IOException("HTTP " + code + " " + conn.getResponseMessage() + " from " + urlStr);
        }
        try (InputStreamReader isr = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(isr).getAsJsonObject();
        }
    }

    private static byte[] downloadBytes(String urlStr, Proxy proxy) throws IOException {
        HttpURLConnection conn = openConnection(urlStr, proxy);
        int code = conn.getResponseCode();
        if (code != 200) {
            throw new IOException("HTTP " + code + " " + conn.getResponseMessage() + " from " + urlStr);
        }
        return conn.getInputStream().readAllBytes();
    }

    private static HttpURLConnection openConnection(String urlStr, Proxy proxy) throws IOException {
        URL url = URI.create(urlStr).toURL();
        HttpURLConnection conn = (HttpURLConnection) (proxy != null ? url.openConnection(proxy) : url.openConnection());
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setRequestProperty("Accept", "*/*");
        return conn;
    }

    private static byte[] extractAudioSpecificConfig(byte[] initBytes) {
        int esds = -1;
        for (int i = 0; i < initBytes.length - 4; i++) {
            if (initBytes[i] == (byte)'e' && initBytes[i+1] == (byte)'s' && initBytes[i+2] == (byte)'d' && initBytes[i+3] == (byte)'s') {
                esds = i;
                break;
            }
        }
        if (esds == -1) return null;

        for (int i = esds; i < esds + 60 && i < initBytes.length - 3; i++) {
            if (initBytes[i] == 0x05) { // DecSpecificInfoTag
                int p = i + 1;
                while (p < initBytes.length && (initBytes[p] & 0x80) != 0) p++;
                if (p >= initBytes.length) break;
                int len = initBytes[p++] & 0xFF;
                if (p + len <= initBytes.length && len > 0) {
                    byte[] asc = new byte[len];
                    System.arraycopy(initBytes, p, asc, 0, len);
                    return asc;
                }
            }
        }
        return null;
    }

    private static List<Integer> parseTrunSizes(byte[] segBytes) {
        List<Integer> sizes = new ArrayList<>();
        int trun = -1;
        for (int i = 0; i < segBytes.length - 4; i++) {
            if (segBytes[i] == (byte)'t' && segBytes[i+1] == (byte)'r' && segBytes[i+2] == (byte)'u' && segBytes[i+3] == (byte)'n') {
                trun = i;
                break;
            }
        }
        if (trun == -1 || trun + 16 > segBytes.length) return sizes;

        ByteBuffer buf = ByteBuffer.wrap(segBytes);
        buf.position(trun + 4);
        int version = buf.get() & 0xFF;
        int flags = ((buf.get() & 0xFF) << 16) | (buf.getShort() & 0xFFFF);
        int sampleCount = buf.getInt();

        if ((flags & 1) != 0) buf.getInt(); // dataOffset
        if ((flags & 4) != 0) buf.getInt(); // firstSampleFlags

        for (int i = 0; i < sampleCount && buf.remaining() >= 4; i++) {
            if ((flags & 0x100) != 0) buf.getInt(); // duration
            int size = buf.getInt();
            sizes.add(size);
            if ((flags & 0x400) != 0) buf.getInt(); // flags
            if ((flags & 0x800) != 0) buf.getInt(); // composition time
        }
        return sizes;
    }

    private static int findMdatOffset(byte[] segBytes) {
        for (int i = 0; i < segBytes.length - 4; i++) {
            if (segBytes[i] == (byte)'m' && segBytes[i+1] == (byte)'d' && segBytes[i+2] == (byte)'a' && segBytes[i+3] == (byte)'t') {
                return i + 4;
            }
        }
        return -1;
    }

    private static byte[] createWavHeader(int pcmDataLength, int sampleRate, int channels) {
        int bitsPerSample = 16;
        int byteRate = sampleRate * channels * (bitsPerSample / 8);
        int blockAlign = channels * (bitsPerSample / 8);
        int totalFileLen = pcmDataLength + 36;

        ByteBuffer buf = ByteBuffer.allocate(44);
        buf.put(new byte[]{'R', 'I', 'F', 'F'});
        buf.putInt(Integer.reverseBytes(totalFileLen));
        buf.put(new byte[]{'W', 'A', 'V', 'E'});
        buf.put(new byte[]{'f', 'm', 't', ' '});
        buf.putInt(Integer.reverseBytes(16)); // Subchunk1Size
        buf.putShort(Short.reverseBytes((short) 1)); // PCM = 1
        buf.putShort(Short.reverseBytes((short) channels));
        buf.putInt(Integer.reverseBytes(sampleRate));
        buf.putInt(Integer.reverseBytes(byteRate));
        buf.putShort(Short.reverseBytes((short) blockAlign));
        buf.putShort(Short.reverseBytes((short) bitsPerSample));
        buf.put(new byte[]{'d', 'a', 't', 'a'});
        buf.putInt(Integer.reverseBytes(pcmDataLength));
        return buf.array();
    }

    private static String hashUrl(String url) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(url.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(url.hashCode());
        }
    }
}
