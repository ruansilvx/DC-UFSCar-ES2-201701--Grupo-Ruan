package org.jabref.logic.sharelatex;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.jabref.logic.importer.ImportFormatPreferences;
import org.jabref.model.database.BibDatabaseContext;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.http.client.utils.URIBuilder;
import org.json.JSONObject;
import org.jsoup.Connection;
import org.jsoup.Connection.Method;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public class SharelatexConnector {

    private final String contentType = "application/json; charset=utf-8";
    private final JsonParser parser = new JsonParser();
    private final String userAgent = "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:53.0) Gecko/20100101 Firefox/53.0";
    private Map<String, String> loginCookies = new HashMap<>();
    private String server;
    private String loginUrl;
    private String csrfToken;
    private String projectUrl;

    public String connectToServer(String serverUri, String user, String password) throws IOException {

        this.server = serverUri;
        this.loginUrl = server + "/login";
        Connection.Response crsfResponse;

        crsfResponse = Jsoup.connect(loginUrl).method(Method.GET)
                .execute();

        Document welcomePage = crsfResponse.parse();
        Map<String, String> welcomCookies = crsfResponse.cookies();

        csrfToken = welcomePage.select("input[name=_csrf]").attr("value");

        String json = "{\"_csrf\":" + JSONObject.quote(csrfToken)
                + ",\"email\":" + JSONObject.quote(user) + ",\"password\":" + JSONObject.quote(password) + "}";

        Connection.Response loginResponse = Jsoup.connect(loginUrl)
                .header("Content-Type", contentType)
                .header("Accept", "application/json, text/plain, */*")
                .cookies(welcomCookies)
                .method(Method.POST)
                .requestBody(json)
                .followRedirects(true)
                .ignoreContentType(true)
                .userAgent(userAgent)
                .execute();

        System.out.println(loginResponse.body());
        ///Error handling block
        if (contentType.equals(loginResponse.contentType())) {

            if (loginResponse.body().contains("message")) {
                JsonElement jsonTree = parser.parse(loginResponse.body());
                JsonObject obj = jsonTree.getAsJsonObject();
                JsonObject message = obj.get("message").getAsJsonObject();
                String errorMessage = message.get("text").getAsString();
                System.out.println(errorMessage);

                return errorMessage;
            }

        }

        loginCookies = loginResponse.cookies();

        return "";
    }

    public Optional<JsonObject> getProjects() throws IOException {
        projectUrl = server + "/project";
        Connection.Response projectsResponse = Jsoup.connect(projectUrl)
                .referrer(loginUrl).cookies(loginCookies).method(Method.GET).userAgent(userAgent).execute();

        System.out.println("");

        Optional<Element> scriptContent = Optional
                .of(projectsResponse.parse().select("script#data").first());

        if (scriptContent.isPresent()) {

            String data = scriptContent.get().data();
            JsonElement jsonTree = parser.parse(data);

            JsonObject obj = jsonTree.getAsJsonObject();

            return Optional.of(obj);

        }
        return Optional.empty();
    }

    public void startWebsocketListener(String projectId, BibDatabaseContext database, ImportFormatPreferences prefs)
            throws URISyntaxException {
        long millis = System.currentTimeMillis();
        System.out.println(millis);
        String socketioUrl = server + "/socket.io/1";
        try {
            Connection.Response webSocketresponse = Jsoup.connect(socketioUrl)
                    .cookies(loginCookies)
                    .data("t", String.valueOf(millis)).method(Method.GET).execute();

            System.out.println(webSocketresponse.body());

            String resp = webSocketresponse.body();
            String channel = resp.substring(0, resp.indexOf(":"));

            URI webSocketchannelUri = new URIBuilder(socketioUrl + "/websocket/" + channel).setScheme("ws").build();
            System.out.println("WebSocketChannelUrl " + webSocketchannelUri);
            WebSocketClientWrapper client = new WebSocketClientWrapper(prefs);
            client.createAndConnect(webSocketchannelUri, projectId, database);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //TODO: Does not work
    public void uploadFile(String projectId, Path path) {
        String activeProject = projectUrl + "/" + projectId;
        String uploadUrl = activeProject + "/upload";

        try {
            try (InputStream str = Files.newInputStream(path)) {

                String urlWithParms = uploadUrl + "?folder_id=" + projectId + "&_csrf=" + csrfToken
                        + "&qquuid=28774ed2-ae25-44f1-9388-c78f1c6b8286" + "&qqtotalfilesize="
                        + Long.toString(Files.size(path));

                Connection.Response fileResp = Jsoup.connect(urlWithParms).cookies(loginCookies)
                        .header("Host", "192.168.1.248")
                        .header("Accept", "*/*")
                        .header("Accept-Language", "Accept-Language: de,en-US;q=0.7,en;q=0.3")
                        .header("Accept-Encoding", "gzip, deflate")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .header("Cache-Control", "no-cache")
                        .data("qqfile", path.getFileName().toString(), str)
                        .cookies(loginCookies).ignoreContentType(true)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; WOW64; rv:53.0) Gecko/20100101 Firefox/53.0")
                        .method(Method.POST).execute();

                //TODO: Investigate why they also get send as multipart form request
                System.out.println(fileResp.body());
            }

        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

}
