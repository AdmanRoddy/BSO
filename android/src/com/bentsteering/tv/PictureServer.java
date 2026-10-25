package com.bentsteering.tv;

import android.content.Context;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.json.JSONArray;

/**
 * A small page on the home Wi-Fi for adding the private pictures (blueprints, crawler photos)
 * from a phone or computer: http://<stick address>:8686, with a 4-digit PIN shown on the TV.
 * Pictures go straight into the app's own folder on the stick.
 */
final class PictureServer {
    static final int PORT = 8686;
    private static final long MAX_BYTES = 25L * 1024 * 1024;
    private static final long MAX_ZIP_BYTES = 1500L * 1024 * 1024;
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static PictureServer running;

    private final Context ctx;
    final String pin;

    private PictureServer(Context c) {
        ctx = c;
        pin = String.format(Locale.US, "%04d", new SecureRandom().nextInt(10000));
    }

    static synchronized PictureServer start(Context c) {
        if (running == null) {
            running = new PictureServer(c.getApplicationContext());
            final PictureServer me = running;
            Thread t = new Thread(new Runnable() { @Override public void run() { me.loop(); } }, "pictures");
            t.setDaemon(true);
            t.start();
        }
        return running;
    }

    /** "http://192.168.1.23:8686", or "" when the stick has no Wi-Fi address. */
    static String address() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) return "http://" + a.getHostAddress() + ":" + PORT;
                }
            }
        } catch (Exception ignored) { }
        return "";
    }

    private void loop() {
        ServerSocket server;
        try { server = new ServerSocket(PORT); } catch (IOException e) { return; }
        while (true) {
            try {
                final Socket s = server.accept();
                new Thread(new Runnable() { @Override public void run() { handle(s); } }).start();
            } catch (IOException e) { return; }
        }
    }

    private void handle(Socket s) {
        try {
            s.setSoTimeout(60_000);
            InputStream in = new BufferedInputStream(s.getInputStream());
            OutputStream out = s.getOutputStream();
            String first = readLine(in);
            if (first == null) return;
            String[] req = first.split(" ");
            if (req.length < 2) return;
            String method = req[0], target = req[1];
            Map<String, String> head = new HashMap<>();
            for (String l = readLine(in); l != null && !l.isEmpty(); l = readLine(in)) {
                int c = l.indexOf(':');
                if (c > 0) head.put(l.substring(0, c).trim().toLowerCase(Locale.US), l.substring(c + 1).trim());
            }
            long len = 0;
            try { len = Long.parseLong(head.containsKey("content-length") ? head.get("content-length") : "0"); } catch (NumberFormatException ignored) { }
            String path = target, query = "";
            int q = target.indexOf('?');
            if (q >= 0) { path = target.substring(0, q); query = target.substring(q + 1); }
            Map<String, String> p = params(query);
            String kind = p.containsKey("kind") ? p.get("kind") : "";
            String name = p.containsKey("name") ? p.get("name") : "";

            if ("GET".equals(method) && "/".equals(path)) {
                send(out, 200, "text/html; charset=utf-8", PAGE.getBytes(UTF8));
            } else if ("GET".equals(method) && "/list".equals(path)) {
                send(out, 200, "application/json", new JSONArray(PrivateFiles.list(ctx, kind)).toString().getBytes(UTF8));
            } else if ("GET".equals(method) && "/file".equals(path)) {
                File f = file(kind, name);
                if (f == null || !f.isFile()) { send(out, 404, "text/plain", "not found".getBytes(UTF8)); return; }
                sendFile(out, f);
            } else if ("POST".equals(method) && ("/upload".equals(path) || "/delete".equals(path))) {
                if (!pin.equals(p.get("pin"))) { drain(in, len); send(out, 403, "application/json", "{\"error\":\"Wrong PIN\"}".getBytes(UTF8)); return; }
                if ("/upload".equals(path) && name.toLowerCase(Locale.US).endsWith(".zip") && !PrivateFiles.folder(ctx, kind).isEmpty()) {
                    if (len <= 0 || len > MAX_ZIP_BYTES) { drain(in, len); send(out, 413, "application/json", "{\"error\":\"Zip is empty or too big\"}".getBytes(UTF8)); return; }
                    send(out, 200, "application/json", unzip(in, len, kind).getBytes(UTF8));
                    return;
                }
                File f = file(kind, name);
                if (f == null) { drain(in, len); send(out, 400, "application/json", "{\"error\":\"Pictures only (png, jpg, webp, gif)\"}".getBytes(UTF8)); return; }
                if ("/delete".equals(path)) {
                    drain(in, len);
                    boolean ok = !f.exists() || f.delete();
                    send(out, ok ? 200 : 500, "application/json", ("{\"ok\":" + ok + "}").getBytes(UTF8));
                    return;
                }
                if (len <= 0 || len > MAX_BYTES) { drain(in, len); send(out, 413, "application/json", "{\"error\":\"Picture is empty or over 25 MB\"}".getBytes(UTF8)); return; }
                File tmp = new File(f.getParentFile(), "." + f.getName() + ".part");
                try (FileOutputStream fo = new FileOutputStream(tmp)) {
                    byte[] buf = new byte[65536];
                    long left = len;
                    while (left > 0) {
                        int n = in.read(buf, 0, (int) Math.min(buf.length, left));
                        if (n < 0) break;
                        fo.write(buf, 0, n);
                        left -= n;
                    }
                    if (left > 0) throw new IOException("upload cut short");
                }
                if (f.exists()) f.delete();
                boolean ok = tmp.renameTo(f);
                String to = ok ? Pictures.ingest(ctx, kind, f) : kind;
                send(out, ok ? 200 : 500, "application/json", ("{\"ok\":" + ok + ",\"to\":\"" + to + "\"}").getBytes(UTF8));
            } else {
                drain(in, len);
                send(out, 404, "text/plain", "not found".getBytes(UTF8));
            }
        } catch (Exception ignored) {
        } finally {
            try { s.close(); } catch (IOException ignored) { }
        }
    }

    /** A whole folder as a .zip (what Google Drive downloads): every picture in it is added. */
    private String unzip(InputStream in, long len, String kind) throws IOException {
        File zip = new File(ctx.getCacheDir(), "pictures-upload.zip");
        try (FileOutputStream fo = new FileOutputStream(zip)) {
            byte[] buf = new byte[65536];
            long left = len;
            while (left > 0) {
                int n = in.read(buf, 0, (int) Math.min(buf.length, left));
                if (n < 0) break;
                fo.write(buf, 0, n);
                left -= n;
            }
            if (left > 0) throw new IOException("upload cut short");
        }
        int added = 0, toBadges = 0;
        try (ZipInputStream z = new ZipInputStream(new BufferedInputStream(new FileInputStream(zip)))) {
            byte[] buf = new byte[65536];
            for (ZipEntry e = z.getNextEntry(); e != null; e = z.getNextEntry()) {
                String full = e.getName().replace('\\', '/');
                String base = full.substring(full.lastIndexOf('/') + 1);
                if (e.isDirectory() || full.contains("__MACOSX") || base.startsWith(".") || !Pictures.isPicture(base)) continue;
                String clean = base.replaceAll("[^A-Za-z0-9._ ()-]", "-").replaceAll("^[^A-Za-z0-9]+", "");
                File f = file(kind, clean);
                if (f == null) continue;
                long size = 0;
                try (FileOutputStream fo = new FileOutputStream(f)) {
                    int n;
                    while ((n = z.read(buf)) > 0) { size += n; if (size > MAX_BYTES) break; fo.write(buf, 0, n); }
                }
                if (size > MAX_BYTES) { f.delete(); continue; }
                added++;
                if ("badges".equals(Pictures.ingest(ctx, kind, f)) && !"badges".equals(kind)) toBadges++;
            }
        } finally {
            zip.delete();
        }
        return "{\"ok\":true,\"added\":" + added + ",\"badges\":" + toBadges + "}";
    }

    /** The file for an upload, or null when the kind or name isn't allowed. */
    private File file(String kind, String name) {
        if (name == null || name.isEmpty() || name.length() > 120) return null;
        if (!name.matches("[A-Za-z0-9][A-Za-z0-9._ ()-]*\\.(?i:png|jpe?g|webp|gif)")) return null;
        String dir = PrivateFiles.folder(ctx, kind);
        if (dir.isEmpty()) return null;
        return new File(dir, name);
    }

    private static Map<String, String> params(String query) {
        Map<String, String> m = new HashMap<>();
        for (String kv : query.split("&")) {
            int e = kv.indexOf('=');
            if (e <= 0) continue;
            try { m.put(URLDecoder.decode(kv.substring(0, e), "UTF-8"), URLDecoder.decode(kv.substring(e + 1), "UTF-8")); }
            catch (Exception ignored) { }
        }
        return m;
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') break;
            if (c != '\r') b.write(c);
            if (b.size() > 8192) throw new IOException("header too long");
        }
        if (c == -1 && b.size() == 0) return null;
        return new String(b.toByteArray(), UTF8);
    }

    private static void drain(InputStream in, long len) throws IOException {
        byte[] buf = new byte[8192];
        while (len > 0) { int n = in.read(buf, 0, (int) Math.min(buf.length, len)); if (n < 0) break; len -= n; }
    }

    private static void send(OutputStream out, int code, String type, byte[] body) throws IOException {
        String head = "HTTP/1.1 " + code + " " + (code == 200 ? "OK" : "Error") + "\r\n"
                + "Content-Type: " + type + "\r\nContent-Length: " + body.length + "\r\n"
                + "Cache-Control: no-store\r\nConnection: close\r\n\r\n";
        out.write(head.getBytes(UTF8));
        out.write(body);
        out.flush();
    }

    private static void sendFile(OutputStream out, File f) throws IOException {
        String n = f.getName().toLowerCase(Locale.US);
        String type = n.endsWith(".png") ? "image/png" : n.endsWith(".webp") ? "image/webp" : n.endsWith(".gif") ? "image/gif" : "image/jpeg";
        String head = "HTTP/1.1 200 OK\r\nContent-Type: " + type + "\r\nContent-Length: " + f.length()
                + "\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n";
        out.write(head.getBytes(UTF8));
        try (FileInputStream fi = new FileInputStream(f)) {
            byte[] buf = new byte[65536];
            int r;
            while ((r = fi.read(buf)) > 0) out.write(buf, 0, r);
        }
        out.flush();
    }

    private static final String PAGE = "<!doctype html><html lang=en><head><meta charset=utf-8>"
            + "<meta name=viewport content='width=device-width,initial-scale=1'><title>Bent Steering TV pictures</title><style>"
            + "body{margin:0;background:#070D1F;color:#fff;font:16px system-ui,-apple-system,Segoe UI,Roboto,sans-serif}"
            + "main{max-width:880px;margin:0 auto;padding:24px 16px 60px}h1{font-style:italic;font-weight:900;margin:0 0 6px}"
            + "p{color:#A9B4D0;margin:0 0 16px;line-height:1.45}label{display:block;font-weight:700;margin:0 0 6px}"
            + "input[type=password],input[type=text]{font:inherit;width:9ch;padding:10px 12px;border-radius:10px;border:1px solid #33406b;background:#0B1530;color:#fff;letter-spacing:.3em}"
            + "section{margin:26px 0;padding:18px;border-radius:16px;background:#0B1530}h2{margin:0 0 4px;font-size:20px}"
            + ".drop{display:block;margin:12px 0;padding:22px;border:2px dashed #33406b;border-radius:14px;text-align:center;color:#A9B4D0;cursor:pointer}"
            + ".drop.on{border-color:#E0262F;color:#fff}.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(150px,1fr));gap:10px}"
            + ".tile{position:relative;background:#05091A;border-radius:10px;overflow:hidden}.tile img{display:block;width:100%;height:96px;object-fit:contain}"
            + ".tile span{display:block;padding:4px 8px;font-size:12px;color:#A9B4D0;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}"
            + ".tile button{position:absolute;top:6px;right:6px;border:0;border-radius:8px;background:#E0262F;color:#fff;font-weight:700;padding:4px 8px;cursor:pointer}"
            + ".msg{min-height:22px;color:#A9B4D0}.msg.err{color:#ff8a8a}</style></head><body><main>"
            + "<h1>Bent Steering TV pictures</h1><p>These stay on the Fire Stick only. Blueprints rotate on the home screen; the first three crawler photos show beside the clock.</p>"
            + "<label for=pin>PIN shown on the TV</label><input id=pin type=password inputmode=numeric maxlength=4 autocomplete=off>"
            + "<section data-kind=blueprints><h2>Blueprints</h2><p>Drop the whole folder, or the .zip Google Drive gives you when you download a folder. Screenshots are cleaned up automatically (white lines, no background), and color pictures go to Crawler photos.</p>"
            + "<label class=drop>Choose or drop pictures, a folder or a .zip<input type=file accept='image/*,.zip' multiple hidden></label>"
            + "<label class=drop>Choose a whole folder<input type=file webkitdirectory multiple hidden></label><div class=msg></div><div class=grid></div></section>"
            + "<section data-kind=badges><h2>Crawler photos</h2>"
            + "<label class=drop>Choose or drop pictures<input type=file accept='image/*,.zip' multiple hidden></label><div class=msg></div><div class=grid></div></section>"
            + "</main><script>"
            + "const pin=document.getElementById('pin');pin.value=localStorage.getItem('bsopin')||'';pin.oninput=()=>localStorage.setItem('bsopin',pin.value);"
            + "const clean=n=>n.replace(/[^A-Za-z0-9._ ()-]/g,'-').replace(/^[^A-Za-z0-9]+/,'').slice(-110)||'picture.png';"
            + "async function list(sec){const k=sec.dataset.kind,g=sec.querySelector('.grid');const names=await (await fetch('/list?kind='+k)).json();g.innerHTML='';"
            + "for(const n of names){const t=document.createElement('div');t.className='tile';t.innerHTML='<img alt=\"\"><span></span><button type=button>Remove</button>';"
            + "t.querySelector('img').src='/file?kind='+k+'&name='+encodeURIComponent(n);t.querySelector('span').textContent=n;"
            + "t.querySelector('button').onclick=async()=>{const r=await fetch('/delete?kind='+k+'&name='+encodeURIComponent(n)+'&pin='+pin.value,{method:'POST'});if(!r.ok)return say(sec,'Check the PIN on the TV',1);list(sec)};g.appendChild(t)}"
            + "say(sec,names.length+' on the stick')}"
            + "function say(sec,t,err){const m=sec.querySelector('.msg');m.textContent=t;m.className='msg'+(err?' err':'')}"
            + "const ok=f=>/\\.(png|jpe?g|webp|gif|zip)$/i.test(f.name)&&!f.name.startsWith('.');"
            + "async function send(sec,files){files=files.filter(ok);if(!files.length)return say(sec,'No pictures found there',1);const k=sec.dataset.kind;let n=0,moved=0;"
            + "for(const f of files){say(sec,(/\\.zip$/i.test(f.name)?'Unpacking ':'Sending ')+(++n)+' of '+files.length+'…');"
            + "const r=await fetch('/upload?kind='+k+'&name='+encodeURIComponent(clean(f.name))+'&pin='+pin.value,{method:'POST',body:f});"
            + "const j=await r.json().catch(()=>({}));if(!r.ok){say(sec,(j.error||'Upload failed')+' — '+f.name,1);return}if(j.to==='badges'||j.badges)moved+=(j.badges||1)}"
            + "for(const s of document.querySelectorAll('section'))await list(s);if(moved)say(sec,'Done · '+moved+' color picture'+(moved>1?'s':'')+' went to Crawler photos')}"
            + "function walk(entry){return new Promise(res=>{if(entry.isFile)entry.file(f=>res([f]),()=>res([]));else if(entry.isDirectory){const r=entry.createReader(),all=[];"
            + "const next=()=>r.readEntries(async es=>{if(!es.length){const parts=await Promise.all(all.map(walk));res(parts.flat())}else{all.push(...es);next()}},()=>res([]));next()}else res([])})}"
            + "async function dropped(e){const items=[...(e.dataTransfer.items||[])].map(i=>i.webkitGetAsEntry&&i.webkitGetAsEntry()).filter(Boolean);"
            + "if(!items.length)return [...e.dataTransfer.files];return (await Promise.all(items.map(walk))).flat()}"
            + "for(const sec of document.querySelectorAll('section')){for(const d of sec.querySelectorAll('.drop')){const i=d.querySelector('input');"
            + "i.onchange=()=>send(sec,[...i.files]);d.ondragover=e=>{e.preventDefault();d.classList.add('on')};d.ondragleave=()=>d.classList.remove('on');"
            + "d.ondrop=async e=>{e.preventDefault();d.classList.remove('on');send(sec,await dropped(e))}}list(sec)}"
            + "</script></body></html>";
}
