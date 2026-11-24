import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Properties;

// Sends an email by talking to a mail server (SMTP) directly. No extra libraries needed.
public class EmailSender {
    private static final String CONFIG_FILE = "smtp.properties";
    private static final int TIMEOUT_MS = 15000;

    private static final String TEMPLATE =
            "# SecureNotes email settings (needed only for \"Forgot PIN\").\n"
                    + "# Keep this file private. Do NOT upload it to GitHub or share it.\n"
                    + "#\n"
                    + "# 1. Put the Gmail address that will SEND the code after username=\n"
                    + "# 2. Make an \"App password\" for that Gmail account and put it after password=\n"
                    + "#    (Google Account > Security > 2-Step Verification > App passwords)\n"
                    + "# 3. Save this file.\n"
                    + "#\n"
                    + "# If port 465 does not work on your network, use:  port=587  and  security=starttls\n"
                    + "\n"
                    + "host=smtp.gmail.com\n"
                    + "port=465\n"
                    + "security=ssl\n"
                    + "username=YOUR_GMAIL_ADDRESS\n"
                    + "password=YOUR_APP_PASSWORD\n";

    // ---------- settings file ----------

    public static String configPath() {
        return Paths.get(CONFIG_FILE).toAbsolutePath().toString();
    }

    // Creates the settings file with instructions inside, if it does not exist yet
    public static void createTemplateIfMissing() throws IOException {
        Path path = Paths.get(CONFIG_FILE);
        if (!Files.exists(path)) {
            Files.write(path, TEMPLATE.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static Properties load() {
        Path path = Paths.get(CONFIG_FILE);
        if (!Files.exists(path)) {
            return null;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            Properties p = new Properties();
            p.load(reader);
            return p;
        } catch (IOException e) {
            return null;
        }
    }

    // True when the settings file exists and has been filled in
    public static boolean isConfigured() {
        Properties p = load();
        if (p == null) {
            return false;
        }
        String user = p.getProperty("username", "").trim();
        String pass = p.getProperty("password", "").trim();
        return !user.isEmpty() && !pass.isEmpty()
                && !user.startsWith("YOUR_") && !pass.startsWith("YOUR_");
    }

    // ---------- the emails we send ----------

    public static void sendCode(String to, String code) throws Exception {
        String body = "Your SecureNotes code is:  " + code + "\n\n"
                + "It works for 10 minutes and can be used once.\n\n"
                + "WARNING: using this code to reset your PIN will permanently erase all notes in the app.\n"
                + "If you did not ask for this, ignore this email. Your notes stay locked and safe.\n";
        send(to, "Your SecureNotes PIN reset code", body);
    }

    public static void sendTest(String to) throws Exception {
        send(to, "SecureNotes test email",
                "This is a test from SecureNotes.\n\nEmail sending works, so the \"Forgot PIN\" code will reach you.\n");
    }

    // ---------- talking to the mail server ----------

    private static void send(String to, String subject, String body) throws Exception {
        Properties cfg = load();
        if (cfg == null || !isConfigured()) {
            throw new Exception("The email settings are not filled in yet.\nOpen " + configPath() + " and follow the steps inside it.");
        }
        String host = cfg.getProperty("host", "smtp.gmail.com").trim();
        String user = cfg.getProperty("username").trim();
        String pass = cfg.getProperty("password").replace(" ", "").trim();   // app passwords are shown with spaces
        String from = cfg.getProperty("from", user).trim();
        int port;
        try {
            port = Integer.parseInt(cfg.getProperty("port", "465").trim());
        } catch (NumberFormatException e) {
            throw new Exception("The port number in smtp.properties is not a number.");
        }
        String security = cfg.getProperty("security", port == 465 ? "ssl" : "starttls").trim().toLowerCase();
        if (!security.equals("ssl") && !security.equals("starttls")) {
            throw new Exception("In smtp.properties, security must be ssl or starttls.");
        }
        if (!safeAddress(to) || !safeAddress(from)) {
            throw new Exception("An email address contains characters that are not allowed.");
        }

        try {
            deliver(host, port, security, user, pass, from, to, subject, body);
        } catch (UnknownHostException e) {
            throw new Exception("Could not find the mail server (" + host + ").\nCheck your internet connection.");
        } catch (ConnectException | SocketTimeoutException e) {
            throw new Exception("Could not connect to the mail server.\nCheck your internet. Some networks block port 465:\ntry port=587 and security=starttls in smtp.properties.");
        } catch (SSLException e) {
            throw new Exception("A secure connection to the mail server could not be made.\n(" + e.getMessage() + ")");
        } catch (SmtpFailure e) {
            if (e.code == 535 || e.code == 534 || e.code == 530 || e.code == 538) {
                throw new Exception("The mail server refused the sign-in.\nCheck the username and App Password in smtp.properties.\n(Gmail needs an App Password, not your normal password.)");
            }
            throw new Exception("The mail server said: " + e.getMessage());
        } catch (IOException e) {
            throw new Exception("Could not send the email: " + e.getMessage());
        }
    }

    private static boolean safeAddress(String address) {
        return address != null && address.matches("[^\\s<>\"\\r\\n]+@[^\\s<>\"\\r\\n]+");
    }

    private static void deliver(String host, int port, String security, String user, String pass,
                                String from, String to, String subject, String body) throws IOException {
        Socket plain = new Socket();
        plain.connect(new InetSocketAddress(host, port), TIMEOUT_MS);
        plain.setSoTimeout(TIMEOUT_MS);
        Socket socket = security.equals("ssl") ? makeSecure(plain, host, port) : plain;

        Smtp smtp = new Smtp(socket);
        try {
            smtp.expect(smtp.read(), 220);
            smtp.command("EHLO securenotes.local", 250);
            if (security.equals("starttls")) {
                smtp.command("STARTTLS", 220);
                smtp.attach(makeSecure(socket, host, port));
                smtp.command("EHLO securenotes.local", 250);
            }
            smtp.command("AUTH LOGIN", 334);
            smtp.command(base64(user), 334);
            smtp.command(base64(pass), 235);
            smtp.command("MAIL FROM:<" + from + ">", 250);
            smtp.command("RCPT TO:<" + to + ">", 250, 251);
            smtp.command("DATA", 354);
            smtp.write(buildMessage(from, to, subject, body) + "\r\n.");
            smtp.expect(smtp.read(), 250);
            try {
                smtp.write("QUIT");
            } catch (IOException ignored) {
                // the email is already sent
            }
        } finally {
            smtp.close();
        }
    }

    // Turns a plain connection into a secure one, and checks the server really is who it says
    private static SSLSocket makeSecure(Socket base, String host, int port) throws IOException {
        SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        SSLSocket secure = (SSLSocket) factory.createSocket(base, host, port, true);
        SSLParameters params = secure.getSSLParameters();
        params.setEndpointIdentificationAlgorithm("HTTPS");   // the certificate must match the server name
        secure.setSSLParameters(params);
        secure.setSoTimeout(TIMEOUT_MS);
        secure.startHandshake();
        return secure;
    }

    private static String base64(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    private static String buildMessage(String from, String to, String subject, String body) {
        String encodedBody = Base64.getMimeEncoder(76, new byte[]{'\r', '\n'})
                .encodeToString(body.getBytes(StandardCharsets.UTF_8));
        return "From: SecureNotes <" + from + ">\r\n"
                + "To: " + to + "\r\n"
                + "Subject: " + subject + "\r\n"
                + "Date: " + ZonedDateTime.now().format(DateTimeFormatter.RFC_1123_DATE_TIME) + "\r\n"
                + "MIME-Version: 1.0\r\n"
                + "Content-Type: text/plain; charset=UTF-8\r\n"
                + "Content-Transfer-Encoding: base64\r\n"
                + "\r\n"
                + encodedBody;
    }

    // ---------- a small helper that speaks the mail server's language ----------

    private static class SmtpFailure extends IOException {
        final int code;

        SmtpFailure(int code, String text) {
            super(text);
            this.code = code;
        }
    }

    private static class Reply {
        final int code;
        final String text;

        Reply(int code, String text) {
            this.code = code;
            this.text = text;
        }
    }

    private static class Smtp {
        private Socket socket;
        private BufferedReader in;
        private BufferedWriter out;

        Smtp(Socket socket) throws IOException {
            attach(socket);
        }

        void attach(Socket s) throws IOException {
            socket = s;
            in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8));
        }

        void write(String line) throws IOException {
            out.write(line + "\r\n");
            out.flush();
        }

        // Reads one reply (a reply can have several lines: "250-..." then "250 ...")
        Reply read() throws IOException {
            StringBuilder text = new StringBuilder();
            while (true) {
                String line = in.readLine();
                if (line == null) {
                    throw new IOException("The mail server closed the connection.");
                }
                if (line.length() < 3) {
                    throw new IOException("The mail server sent something unexpected.");
                }
                text.append(text.length() > 0 ? "\n" : "").append(line.length() > 4 ? line.substring(4) : "");
                if (line.length() == 3 || line.charAt(3) != '-') {
                    int code;
                    try {
                        code = Integer.parseInt(line.substring(0, 3));
                    } catch (NumberFormatException e) {
                        throw new IOException("The mail server sent something unexpected.");
                    }
                    return new Reply(code, text.toString());
                }
            }
        }

        void expect(Reply reply, int... okCodes) throws IOException {
            for (int ok : okCodes) {
                if (reply.code == ok) {
                    return;
                }
            }
            throw new SmtpFailure(reply.code, reply.text);
        }

        void command(String line, int... okCodes) throws IOException {
            write(line);
            expect(read(), okCodes);
        }

        void close() {
            try {
                socket.close();
            } catch (IOException ignored) {
                // nothing to do
            }
        }
    }
}